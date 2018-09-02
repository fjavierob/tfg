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

import java.util.List;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.bluetooth.BluetoothDevice;
import org.eclipse.kura.bluetooth.BluetoothGatt;
import org.eclipse.kura.bluetooth.BluetoothGattCharacteristic;
import org.eclipse.kura.bluetooth.BluetoothGattService;
import org.eclipse.kura.bluetooth.BluetoothLeNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**     DISPOSITIVO HRP
 * 
 * @author fcoortbon
 * 
 * Cada objeto de esta clase representa un dispositivo bluetooth real con el que
 * se interacciona.
 * 
 * Contiene distintos métodos para conectarse y desconectarse del dispositivo, 
 * consultar el estado de la conexión, obtener los servicios que ofrece, etc.
 *
 */

public class HRPDevice implements BluetoothLeNotificationListener 
{

    private static final Logger logger = LoggerFactory.getLogger(HRPDevice.class);

    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice device;
    private boolean isConnected;
    private String myAddress;
    private HRPClient central;
    
    private static final int MAX_ENERGY = 60000;
    private int resetEnergy;
    
   		
    public HRPDevice(BluetoothDevice bluetoothDevice, HRPClient central) 
    {
    	this.central = central;
        this.device = bluetoothDevice;
        this.myAddress = device.getAdress();
        this.isConnected = false;
        this.resetEnergy = 0;
    }

    public BluetoothDevice getBluetoothDevice() 
    {
        return this.device;
    }

    public void setBluetoothDevice(BluetoothDevice device) 
    {
        this.device = device;
    }

    /**
     * Método para comprobar la conexión con el dispositivo.
     * @return True si está conectado, false en caso contrario.
     */
    public boolean isConnected() 
    {
        if (this.isConnected) this.isConnected = checkConnection();
        logger.info("isConnected() "+device.getAdress()+": "+isConnected);
        return this.isConnected;
    }

    /**
     * Método para conectarse al dispositivo.
     * @param adapterName Nombre del adaptador bluetooth.
     * @return True si se consiguió conectar, false en caso contrario.
     */
    public boolean connect(String adapterName) 
    {
        this.bluetoothGatt = this.device.getBluetoothGatt();
        boolean connected = false;
        try 
        {
            connected = this.bluetoothGatt.connect(adapterName);
        } 
        catch (KuraException e) 
        {
            logger.error(e.toString());
        }
        if (connected) 
        {
            this.bluetoothGatt.setBluetoothLeNotificationListener(this);
            this.isConnected = true;
            return true;
        } 
        else 
        {
            // If connect command is not executed, close gatttool
            this.bluetoothGatt.disconnect();
            this.isConnected = false;
            return false;
        }
    }
    
    /**
     * Método para desconectarse del dispositivo.
     */
    public void disconnect() 
    {
        if (this.bluetoothGatt != null) 
            this.bluetoothGatt.disconnect();
        this.isConnected = false;
    }
    
    /**
     * Método para obtener la dirección MAC del dispositivo.
     * @return Dirección MAC del dispositivo.
     */
    public String getAddress()
    {
    	return myAddress;
    }

    /**
     * Método para comprobar la conexión con el dispositivo.
     * @return True si está conectado, false en caso contrario.
     */
    public boolean checkConnection() 
    {
        if (this.bluetoothGatt != null) 
        {
            boolean connected = false;
            try 
            {
                connected = this.bluetoothGatt.checkConnection();
            } 
            catch (KuraException e) 
            {
                logger.error(e.toString());
            }
            if (connected) 
            {
                this.isConnected = true;
                return true;
            } 
            else 
            {
                // If connect command is not executed, close gatttool
                this.bluetoothGatt.disconnect();
                this.isConnected = false;
                return false;
            }
        } 
        else 
        {
            this.isConnected = false;
            return false;
        }
    }
    
        
    /**
     * Método para comprobar si el dispositivo ofrece el servicio de pulso cardíaco.
     * @return True si el dispositivo ofrece el servicio de pulso cardíaco, false
     * en caso contrario.
     */
    public boolean hasHRService()
    {
    	
    	List<BluetoothGattService> servicios = this.discoverServices();
    	for (BluetoothGattService servicio : servicios)
    	{
    		if (servicio.getUuid().equals(HRPGatt.UUID_HEART_RATE_SERVICE))
    			return true;
    	}
    	return false;
    	
    }
    


    
    /**
     * Método para obtener la lista de servicios que ofrece el dispositivo.
     * @return Lista de servicios ofrecidos por el dispositivo.
     */
    public List<BluetoothGattService> discoverServices() 
    {
        return this.bluetoothGatt.getServices();
    }
    
/*
    public List<BluetoothGattCharacteristic> getCharacteristics(String startHandle, String endHandle) 
    {
        logger.info("List<BluetoothGattCharacteristic> getCharacteristics");
        return this.bluetoothGatt.getCharacteristics(startHandle, endHandle);
    }
*/
    
// Heart Rate Service
    
    
    /**
     * Método para activar en el dispositivo las notificaciones para las medidas de pulso cardíaco.
     */
    public void enableHrmNotifications()
    {
    	// Escribir "0100" en el CCC de la característica de pulso cardíaco
     	this.bluetoothGatt.writeCharacteristicValue(HRPGatt.HANDLE_HEART_RATE_MEASUREMENT_CCC, "01:00");
    }
    
    /**
     * Método para desactivar en el dispositivo las notificaciones para las medidas de pulso cardíaco.
     */
    public void disableHrmNotifications()
    {
    	// Escribir "0000" en el CCC de la característica de pulso cardíaco
    	this.bluetoothGatt.writeCharacteristicValue(HRPGatt.HANDLE_HEART_RATE_MEASUREMENT_CCC, "00:00");
    }
    
    /**
     * Método para ordenar al dispositivo que resete el campo de energía gastada acumulada.
     */
    public void resetEnergyExpended()
    {
    	// Escribir "01" en la característica Heart Rate Control Point
    	this.bluetoothGatt.writeCharacteristicValue(HRPGatt.HANDLE_HEART_RATE_CONTROL_POINT, "01");
    }
    
    
// Device Information Service
    
    /**
     * Método para obtener el firmware del dispositivo.
     * @return Firmware del dispositivo.
     */
    public String firmwareRevision() 
    {
        String firmwareVersion = "";
        try 
        {
        	String firmware = this.bluetoothGatt.readCharacteristicValueByUuid(HRPGatt.UUID_HARDWARE_REVISION);
        	firmwareVersion = hexAsciiToString(firmware.substring(0, firmware.length() - 3));
        } 
        catch (KuraException e) 
        {        	
            logger.error(e.toString());
        }
        return firmwareVersion;
    }
    
// BluetoothLeNotificationListener
    
	@Override
	public void onDataReceived(String handle, String value)
	{
		// La información viene byte a byte (en hexadecimal -> dos caracteres) separada por espacios
		String[] parts = value.split(" ");
		// Pulso: segundo byte
		int pulso = Integer.parseInt(parts[1], 16);
		int energia = -1;
		// Si tamaño de la cadena es 11 es porque viene incluida medida de energía gastada acumulada.
		// 11 caracteres = 2 (opciones) + 1 (espacio) + 2 (pulso) + 1 (espacio) + 2 (energia byte menos sig) + 1 (espacio) + 2 (energia byte mas sig)
		if (value.length() == 11)
		{
			// Calcular medida de energia gastada acumulada
			energia = Integer.parseInt(parts[2], 16) + 256*Integer.parseInt(parts[3], 16);
			logger.info("Datos recibidos: Address = "+myAddress+"Pulso = " + pulso + "bpm, Energia = " + energia + "kJ (" + (resetEnergy+energia) +"kJ)");
			if (energia >= MAX_ENERGY)
			{
				/* #REVISAR resetEnergyExpended()
				this.resetEnergy += energia;
		    	logger.debug("Reset energy expended.");
				resetEnergyExpended(); */
			}
		}
		
		else logger.info("Datos recibidos: Pulso = " + pulso + "bpm.");
		
		if (energia != -1) energia += resetEnergy;
		central.publicarDatos(myAddress, pulso, energia);
		
	}
    
// Métodos auxiliares

    private String hexAsciiToString(String hex) 
    {
    	hex = hex.replaceAll(" ", "");
    	StringBuilder output = new StringBuilder();
    	for (int i = 0; i < hex.length(); i += 2)
    	{
    		String str = hex.substring(i, i + 2);
    		output.append((char) Integer.parseInt(str, 16));
    	}
    	return output.toString();
    }

}
