/**
 *  -------------------------------------------------------------------
 *  |   Fco. Javier Ortiz Bonilla.                                    |
 *  |   Trabajo fin de grado. Curso 2016/2017.                        |
 *  |                                                                 |
 *  |   Departamento de Ingeniería Telemática.                        |
 *  |   Grado en Ingeniería de las Tecnologías de Telecomunicación.   |
 *  |   Escuela Técnica Superior de Ingeniería, Sevilla.              |
 *  -------------------------------------------------------------------
 */

 /**     SENSOR DE PULSO EMULADO
  *
  * @author fcoortbon
  *
  * Mediante este programa se va a emular un sensor que mide el pulso cardíaco y la energía gastada y
  * que puede notificar esta información usando Bluetooth Low Energy.
  *
  * Para ello, se va a utilizar un módulo llamado bleno que sirve para implementar periféricos BLE:
  *   https://github.com/sandeepmistry/bleno
  * Y vamos a implementar el servicio de pulso tal y como se describe en la especificación Bluetooth
  * del servicio Gatt 'Heart Rate': 
  *   https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.service.heart_rate.xml
  * 
  * Para poder poner en funcionamiento este emulador, se necesita un adaptador Bluetooth 4.0 ó superior,
  * y tener las dependencias necesarias instaladas para el uso de Bluetooth en Linux: 
  * Ver https://github.com/sandeepmistry/bleno
  *
  * Además, será necesario que el servicio de Bluetooth (bluetoothd) se desactive primero para que el 
  * programa pueda funcionar bien.
  * 
  */

  /**     EJEMPLOS DE EJECUCIÓN
    * 
    * sudo node ble_hrm.js  
    *   Ejecución estándar que usa por defecto el primer adaptador bluetooth que encuentre (hci0).
    *
    * sudo BLENO_HCI_DEVICE_ID=2 BLENO_ADVERTISING_INTERVAL=1000 node ble_hrm.js 
    *   Ejecución indicando qué adaptador se quiere usar y cada cuanto tiempo (en ms) se envían
    *   paquetes de anunciamiento.
    *  
    */ 

// Servicio de pulso cardíaco: números asignados
const SERVICIO_HEART_RATE = '180d';
const CARACT_HEART_RATE_MEASUREMENT = '2a37';
const CARACT_BODY_SENSOR_LOCATION = '2a38';
const CARACT_HEART_RATE_CONTROL_POINT = '2a39';
	// Los Client Characteristic Configuration se implementan automáticamente para cada característica.
const CODIGO_RESET_ENERGY = 0x01;
const CODIGO_SENSOR_MUÑECA = "02";

// Parámetros
var periodoEnvio = 3000; // Tiempo en ms entre notificaciones de medidas de pulso cardíaco.
var freqEnergia = 3; // En una de cada freqEnergia medidas de pulso se incluye la medida de energía acumulada gastada.

// Variable usada para incluir o no medida de energía. Con cada medida de pulso se incrementará en uno, y
// si es igual a freqEnergia se incluriá medida de energía y se establecerá su valor a 1.
var index = 1;

// Variable en la que se acumula la energia gastada acumulada.
var expendedEnergy = 0;

// Variable en la que se almacena si hay un dispositivo suscrito al valor de la medida de pulso.
var subscribed = false;

// Función auxiliar que devuelve un número aleatorio comprendido entre dos valores.
function randomInt (low, high) 
{
    return Math.floor(Math.random() * (high - low + 1) + low);
}
 
/*
 * Función auxiliar para construir un valor para la característica Heart Rate Measurement.
 * Contiene siempre un campo de flags y el pulso (uint8 ó uint16).
 * Campos:
 *  - Flags (1 byte): 
 *     0:   uint8 para pulso.
 *     01:  sensor de contacto con la piel no soportado.
 *     1/0: campo de energía gastada presente (1) ó no (0), dependiendo de si se incluye energía.
 *     0:   campo RR-interval no presente.
 *     000: bits para uso futuro a 0.
 *  - Medida de pulso (uint8).
 *  - Energía gastada ACUMULADA (2 bytes):
 *     [!!] Se incluirá este campo si includeEE === true.
 *  - RR-Interval: NO SE USA.
 */
function makeHRM(iBPM, sBPM, iEE, sEE, includeEE)
{

	var pulso = randomInt(iBPM, sBPM);
	if (includeEE)
	{
		expendedEnergy += randomInt(iEE, sEE);
		if (expendedEnergy > 65535)
			expendedEnergy = 65535;
		var HRM = new Buffer(4);
		HRM.writeUInt16LE(expendedEnergy, 2);
		console.log("Pulso: "+pulso+"bpm, Energia: "+expendedEnergy+"kJ !");
		HRM[0] = 0b00110000;
	}
	else
	{
		var HRM = new Buffer(2);
		console.log("Pulso: "+pulso+"bpm");
		HRM[0] = 0b00100000;
	}
	
	HRM.writeUInt8(pulso, 1);

	return HRM;	
}

// Módulo bleno
var bleno = require('bleno');

// Cuando se inicie bleno, empezamos a anunciar nuestro sensor emulado.
bleno.on('stateChange', function(state) {
    console.log('State change: ' + state);
    if (state === 'poweredOn') {
        bleno.startAdvertising('SensorEmulado',['180d']);
    } else {
        bleno.stopAdvertising();
        clearInterval(this.intervalId);
    }
});
 
// Cuando se acepta una conexión con otro dispositivo.
bleno.on('accept', function(clientAddress) {
    console.log("Aceptada conexion de: " + clientAddress);
});
 
// Cuando nos desconectamos de un dispositivo con el que estabamos conectado.
bleno.on('disconnect', function(clientAddress) {
    console.log("Desconectado de: " + clientAddress);
});
 
// Creamos nuestro servicio de pulso cardíaco cuando empecemos a anunciarnos.
bleno.on('advertisingStart', function(error) {
    if (error) {
        console.log("Error al empezar a anunciarse:" + error);
    } else {
        console.log("Anunciandose...");
        // Servicios que ofrecemos
        bleno.setServices([
            // Servicio de pulso cardíaco
            new bleno.PrimaryService({
                uuid : SERVICIO_HEART_RATE,
                // Características dentro del servicio
                characteristics : [
                    
                    // Característica Heart Rate Measurement
                    new bleno.Characteristic({
                        value : null,
                        uuid : CARACT_HEART_RATE_MEASUREMENT,
                        properties : ['notify'],
                        
                        // Si se suscriben a esta característica, cada periodoEnvio ms
                        // enviamos una nueva medida de pulso.
                        onSubscribe : function(maxValueSize, updateValueCallback) {
                       		clearInterval(this.intervalId);
                           	console.log("Cliente suscrito a caracteristica Heart Rate Measurement !");
                           	this.intervalId = setInterval(function() {
                           		var includeEE = false;
                           		// Incluimos medida de energia acumulada gastada una vez cada
                           		// freqEnergia veces.
                           		if (index == freqEnergia)
                           		{
                           			includeEE = true;
                           			index = 0;
                           		}
                           		index++;
                           		var HeartRateMeasurement = makeHRM(80,140,10,15,includeEE)
                               	updateValueCallback(HeartRateMeasurement);
                           	}, periodoEnvio);
                        },
                        
                        // Si el cliente se desuscribe, dejamos de enviar medidas de pulso.
                        onUnsubscribe : function() {
                            console.log("Cliente desuscrito a caracteristica Heart Rate Measurement.");
                            clearInterval(this.intervalId);
                        }
 
                    }),

                    // Característica Body Sensor Location
                    new bleno.Characteristic({
                        value : new Buffer(CODIGO_SENSOR_MUÑECA, "hex"), // En la muñeca
                        uuid : CARACT_BODY_SENSOR_LOCATION,
                        properties : ['read'],
                                             
                        // Send a message back to the client with the characteristic's value
                        // Nunca se llama a este métoddo, se lee value directamente (whyesa?)
                        onReadRequest : function(offset, callback) {
                            console.log("Peticion de lectura de caracteristica Body Sensor Location");
                            callback(this.RESULT_SUCCESS, this.value);
                        }                        
                    }),

                    // Característica Heart Rate Control Point
                    new bleno.Characteristic({
                        value : null,
                        uuid : CARACT_HEART_RATE_CONTROL_POINT,
                        properties : ['write'],

                        // Petición de escritura
                        onWriteRequest : function(data, offset, withoutResponse, callback) {
                            // this.value = data;
                            if (data.toString("hex") == CODIGO_RESET_ENERGY)
                            {
                            	// Se ha pedido reiniciar el contador de energía gastada acumulada
                            	console.log('Recibida orden de reiniciar el contador de energia...');
                            	expendedEnergy = 0;
                            	console.log('Contador de energia gastada acumulada reiniciado !');
                            	callback(this.RESULT_SUCCESS);
                            }
                            else
                            {
                            	console.log('Heart Rate Control Point: Recibido codigo desconocido: ' + data.toString("hex"));
                            	callback(0x80);
                            }
                        }
 
                    })
				]
            })
        ]);
    }
});
