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

package fcoortbon.ble.central_hrp;

import java.util.UUID;

public class HRPGatt
{
	/**     GATT HRP
	 * 
	 * @author fcoortbon
	 * 
	 * Clase en la que se recogen los UUID de los servicios de pulso e información de dispositivo.
	 * 
	 */
	
	public static final UUID UUID_CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	
	/* Heart Rate Service */
	
	public static final UUID UUID_HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
	                                                                    
	public static final UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_BODY_SENSOR_LOCATION = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_HEART_RATE_CONTROL_POINT = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb");	
	
	public static final String HANDLE_HEART_RATE_MEASUREMENT = "0x000c";
	
	public static final String HANDLE_HEART_RATE_MEASUREMENT_CCC = "0x000d";
	
	public static final String HANDLE_BODY_SENSOR_LOCATION = "0x000f";
	
	public static final String HANDLE_HEART_RATE_CONTROL_POINT = "0x0011";	
	
	
	/* Device Information Service */
	
	public static final UUID UUID_MANUFACTURER_NAME=UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_MODEL_NUMBER=UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_SERIAL_NUMBER=UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");

	public static final UUID UUID_FIRMWARE_REVISION=UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_HARDWARE_REVISION=UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_SOFTWARE_REVISION=UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_SYSTEM_ID=UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_IEEE_REGULATORY_CERTIFICATION_DATA_LIST=UUID.fromString("00002a2a-0000-1000-8000-00805f9b34fb");
	
	public static final UUID UUID_PNP_ID=UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb");
	
	public static final String HANDLE_MANUFACTURER_NAME="0x0014";
	
	public static final String HANDLE_MODEL_NUMBER="0x0016";
	
	public static final String HANDLE_SERIAL_NUMBER="0x0018";

	public static final String HANDLE_FIRMWARE_REVISION="0x001a";
	
}
