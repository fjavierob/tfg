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


import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.SimpleTimeZone;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraConnectException;
import org.eclipse.kura.KuraTimeoutException;
import org.eclipse.kura.KuraNotConnectedException;
import org.eclipse.kura.bluetooth.BluetoothAdapter;
import org.eclipse.kura.bluetooth.BluetoothDevice;
//import org.eclipse.kura.bluetooth.BluetoothGattCharacteristic;
//import org.eclipse.kura.bluetooth.BluetoothGattService;
import org.eclipse.kura.bluetooth.BluetoothLeScanListener;
import org.eclipse.kura.bluetooth.BluetoothGattService;
import org.eclipse.kura.bluetooth.BluetoothService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.data.DataService;
import org.eclipse.kura.data.listener.DataServiceListener;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**     CLIENTE HRP
 * 
 * @author fcoortbon
 * 
 * Esta clase implementa un cliente para dispositivos bluetooth LE, en concreto,
 * para dispositivos que ofrezcan el servicio de pulso cardíaco.
 * 
 * Permite ser controlado a través de la nube, recibiendo mensajes de control en
 * un topic de control concreto.
 * 
 * Inicialmente el bundle está en estado idle, activado pero sin hacer nada, a la espera
 * de órdenes. Cuando llega una orden de iniciar una nueva sesión, comenzará a funcionar:
 * Empezará a realizar periódicamente escaneos para encontrar dispositivos bluetooth LE e
 * intentará conectarse a estos. Una vez se ha conectado a ellos, comprueba que ofrecen 
 * el servicio de pulso cardíaco y entonces activa las notificaciones para esta característica.
 * 
 * Para cada dispositivo bluetooth se crea un nuevo objeto HRPDevice que lo identifica, y es
 * este objeto el que recibirá las notificaciones de pulso cardíaco. Cada vez que reciba una nueva medida,
 * llamará al método publicarDatos(String, int, int) de HRPClient para que estos datos sean
 * publicados en la nube.
 * 
 * Cuando se recibe la orden de terminar la sesión, el cliente HRP se desconecta de todos los
 * dispositivos bluetooth a los que estaba conectado, deja de realizar escaneos y pasa a estar
 * en un estado idle de nuevo, a la espera de nuevas órdenes. * 
 *  
 */ 

public class HRPClient implements ConfigurableComponent, BluetoothLeScanListener, 
DataServiceListener
{
	private static final Logger logger = LoggerFactory.getLogger(HRPClient.class);
	
    private List<HRPDevice> HRPDeviceList;
    private List<String> BlackList;
    private Map<String, Integer> connectAttempts;
    private BluetoothService bluetoothService;
    private BluetoothAdapter bluetoothAdapter;
    private ScheduledExecutorService worker;
    private ScheduledFuture<?> handle;

    private static DataService dataService;
    
    private final String APP_ID = "fcoortbon.ble.central_hrp.HRPClient.java";
    private final int MAX_CONNECT_ATTEMPTS = 3;
    
    /* Propiedades configurables*/

    private final String PROPERTY_CODIGO_GIMNASIO ="codigoGim";
    private final String PROPERTY_NUM_DISPOSITIVO = "numDisp";
    private final String PROPERTY_ENABLE = "client_enable";
    private final String PROPERTY_SCANTIME = "scan_time";
    private final String PROPERTY_PERIOD = "period";
    private final String PROPERTY_INAME = "iname";
    
    // valores por defecto
    private String codigoGim = "";
    private int numDisp = 1;
    private boolean client_enable = false;
    private int scantime = 3;
    private int period = 10;
    private String iname = "hci0";

    private static String topic = "";
    private static String controlTopic="";
    private static String statusTopic="";
    
    private long startTime;
    private boolean connected = false;
    
    private boolean started = false;
    
    private static Date inicioSesion;
    private static Date finSesion;
    static DateFormat formatoFecha = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'");
    
    private JSONObject estado;
    

    public void setBluetoothService(BluetoothService bluetoothService)
    {
        this.bluetoothService = bluetoothService;
    }

    public void unsetBluetoothService(BluetoothService bluetoothService) 
    {
        this.bluetoothService = null;
    }
    
	public void setDataService(DataService dataService) {
		this.dataService = dataService;
	}

	public void unsetDataService(DataService dataService) {
		this.dataService = null;
	}
    
	/**
	 * Método para publicar medidas de pulso recibidas de un dispositivo bluetooth.
	 * @param address Dirección del dispositivo bluetooth.
	 * @param pulso Medida de pulso.
	 * @param energia Medida de energía gastada acumulada. -1 si no estaba presente.
	 */
    protected static void publicarDatos(String address, int pulso, int energia)
    {
    	String _inicioSesion = formatoFecha.format(inicioSesion);
    	logger.info("Publicar datos: "+address+" -> pulso: "+pulso+(energia!=-1 ? (", energia: "+energia) : ""));
		String timestamp = formatoFecha.format(new Date());
		
		// Construimos el mensaje JSON
		JSONObject datos = new JSONObject();
		if (energia != -1)
			datos.put("energia", energia);
		datos.put("pulso", pulso);	
		datos.put("address", address);
		datos.put("fechaSesion", _inicioSesion);
		datos.put("timestamp", timestamp);

		byte[] payload = datos.toJSONString().getBytes();
        
		// Publicamos el mensaje
        if (publish(topic, payload))
        	logger.info("Datos publicados!");
    }
	
    
    // Método que se llamará cuando se arranque el bundle.
    protected void activate(ComponentContext context, Map<String, Object> properties) 
    {
       logger.info("Activando cliente HRP...");
        
       readProperties(properties);
       logConfiguracion();
  
       this.HRPDeviceList = new ArrayList<HRPDevice>();
       this.BlackList = new ArrayList<String>();
       this.connectAttempts = new HashMap<String, Integer>();
      
       dataService.addDataServiceListener(this);
       
       if (!dataService.isConnected())
       {
   			logger.info("Conectando a la nube...");
   			try {
   				dataService.connect();
   			}
   			catch (KuraConnectException e)
   			{
   				logger.error("Error al conectar a la nube", e);
   			}
       }
       
       formatoFecha.setTimeZone(new SimpleTimeZone(SimpleTimeZone.UTC_TIME, "UTC"));
       
       this.estado = new JSONObject();

       this.estado.put("estado", ControlHRPClient.ESTADO_IDLE);
       this.estado.put("fechaIni", "Unknown");
       this.estado.put("fechaFin", "Unknown");
       
    }
    
    // Método llamado cuando se desactiva el bundle.
    protected void deactivate(ComponentContext context) 
    {
        logger.info("Desactivando cliente HRP...");
        stop();
        if (dataService.isConnected()) dataService.disconnect(5);
        logger.info("Cliente HRP desactivado.");
        
    }

    // Método que se llamará cuando se cambie la configuración del bundle, esto es, 
    // los parámetros configurables.
    protected void updated(Map<String, Object> properties) 
    {
    	logger.info("CLiente HRP: Actualizando estado...");
    	
    	// Leemos las propiedades
        readProperties(properties);
        logConfiguracion();
        
    	// A partir de la propiedad PROPERTY_ENABLE (client_enable) damos la posibilidad de
    	// iniciar y terminar sesiones desde la interfaz web y no a través de órdenes 
    	// enviadas desde la nube.
        if (started)
        {
        	// Ya en sesión.
        	
        	if(client_enable)
        	{
        		// No hacemos nada.
        	}
        	else
        	{
        		// Se considera que la sesión quiere darse por finalizada. Se finaliza entonces.
        		endSession();
        	}
        }
        else
        {
        	// No estamos en sesión.
        	
        	if (client_enable)
        	{
        		// Estaba deshabilitado antes y al actualizar, se habilita.
        		// Se empieza una nueva sesión.
        		startNewSession();
        	}
        	else
        	{
        		// No hacemos nada.
        	}
        }
     }
    
    
    //////////////////////////////////////////////////////////////
    //										                    //
    // TAREA PRINCIPAL DURANTE LA SESION EJECUTADA CADA SEGUNDO //
    //	  									                    //
    //////////////////////////////////////////////////////////////
    void checkScan() 
    {
        // Escanear dispositivos
        if (this.bluetoothAdapter.isScanning()) 
        {
            logger.info("Escaneando...");
            // Paramos el escaneo si lleva escaneando más tiempo que el tiempo de escaneo
            if (System.currentTimeMillis() - this.startTime >= this.scantime * 1000) 
            {
            	logger.info("Parando escaneo...");
                this.bluetoothAdapter.killLeScan();
            }
        } 
        else 
        {
        	// Empezamos a escanear si, desde el último escaneo, ha pasado tiempo igual o 
        	// mayor al periodo entre escaneos.
            if (System.currentTimeMillis() - this.startTime >= this.period * 1000) 
            {
                logger.info("Comenzando escaneo...");
                this.bluetoothAdapter.startLeScan(this);
                this.startTime = System.currentTimeMillis();
            }
        }
    }


    //
    
    /* BluetoothLeScanListener */
    
    //
    
    @Override
    public void onScanFailed(int errorCode)
    {
        logger.error("Error durante el escaneo. Codigo: " + errorCode);
    }
    
    /**
     * Método que recibe los resultados de un escaneo Bluetooth LE.
     * @param scanResults Lista de dispositivos bluetooth descubiertos.
     */
    @Override
    public void onScanResults(List<BluetoothDevice> scanResults)
    {
    	
    	//	Puede darse la casualidad de que se finalice la sesión e inmediatamente se reciban resultados
    	//  del escaneo. En este caso, salimos.
    	if (!started) return;
    	
    	// Recorremos la lista de dispositivos descubiertos.
    	 for (BluetoothDevice bluetoothDevice : scanResults) 
    	 {
    		 String address = bluetoothDevice.getAdress();
             logger.info("Encontrado dispositivo " + address + " Name " + bluetoothDevice.getName());

             if (!isInBlackList(address))
             {
	             if (!isInHRPDeviceList(address)) 
	             {
	            	 HRPDevice device = new HRPDevice(bluetoothDevice, this);
	            	 this.HRPDeviceList.add(device);
	             }
	             else
	             {
		             // Un dispositivo LE que está conectado a un cliente no se anuncia. Entonces,
		             // si lo descubro y ya estaba en mi lista, quiere decir que:
		             // - Ó bien me intenté conectar a él pero no pude.
		             // - Ó estaba conectado pero por alguna circunstancia se desconectó
		             // Así que lo desconectamos.
	            	 logger.info(address+" ya en la lista!");
	            	 HRPDevice device = findInHRPDeviceList(address);
	            	 device.disconnect();
	             }
             }
    	 }
    	 
    	 // Conectar a los dispositivos guardados
         for (HRPDevice device : this.HRPDeviceList) 
         {
        	 String address = device.getBluetoothDevice().getAdress();
        	 
        	 // Sólo si no estoy ya conectado...
             if (!device.isConnected())
             {
                 logger.info("Conectando a "+ address +"...");
                 this.connected = device.connect(this.iname);
                 if (this.connected) 
                 {
                	 logger.info("Conectado a "+ address+" !");    
                	 // Si conseguimos conectarnos, reseteamos el contador de intentos de reconexión.
                	 if (connectAttempts.containsKey(address))
                		 connectAttempts.put(address, 0);
                	 logger.info("Comprobar si ofrece HRS...");
                	 boolean tieneHRS = device.hasHRService();
	 
                	 if (tieneHRS)
                	 {
                         logger.info("Activando notificaciones para el pulso en "+ address);
                         device.enableHrmNotifications(); 
                	 }
                	 else
                	 {
                		 device.disconnect();
                		 this.connected=false;
                		 HRPDeviceList.remove(device);
                		 BlackList.add(address);
                		 logger.info("Añadido a lista negra "+address+": No ofrece HRS.");
                	 }
                 }
                 else
                 {
                	 logger.info("No se pudo conectar a " + address+"...");
                	 if (connectAttempts.containsKey(address))
                	 {
                		 int intentos = connectAttempts.get(address);
                		 if (intentos == this.MAX_CONNECT_ATTEMPTS-1)
                    	 {
                    		 // Máximos intentos de conexión. No lo intentaremos más.
                    		 HRPDeviceList.remove(device);
                    		 BlackList.add(address);
                    		 logger.info("Añadido a lista negra "+address+": Maximo numero de intentos de conexion.");
                    	 }
                		 else {
                			 connectAttempts.put(address, intentos+1);
                		 }
                	 }
                	 else {
                		 connectAttempts.put(address, 1);
                	 }
                 }
             } 
             else 
             {
                 logger.info("Ya conectado a "+ address+" !");
                 this.connected = true;
             }
         }
    }
    
    //
    
    /* DataServiceListener */

    //
    
    @Override
    public void onDisconnecting()
    {
    	
    }
    
    @Override
    public void onDisconnected()
    {
    	
    }
    
	@Override
	public void onConnectionLost(Throwable cause) 
	{

	}
    
    @Override
    public void onConnectionEstablished() 
    {
    	// Al establecerse la conexión con la nube nos suscribimos al topic de control para
    	// poder así recibir órdenes.
    	suscribirTopic(controlTopic);
    }

    /**
     * Método que recibe los mensajes que nos llegan desde la nube de topics a los que
     * estemos suscritos (únicamente al topic de control de este propio bundle).
     */
    @Override
	public void onMessageArrived(String topic, byte[] payload, int qos,
			boolean retained) 
    {
    	logger.info("# ------------------------------------------------------------");
    	logger.info("Recibido mensaje en topic "+topic+":");
    	
    	JSONParser parser = new JSONParser();
    	
    	if (topic.equals(controlTopic))
    	{
	    	try 
	    	{
	    		JSONObject json = (JSONObject) parser.parse(new String(payload));
	    		int action = (int) (long) json.get("action");
	    		//int action = Integer.parseInt((String)json.get("action"));
	    		
	    		switch (action)
	    		{
	    			case ControlHRPClient.COMENZAR_SESION:
	    				if (started)
	    				{
	    					// Ya se está dando una sesión.
	    					logger.info("Recibida orden de empezar sesion, pero ya estamos en sesion!");
	    				}
	    				else
	    				{
	    					logger.info("Recibida orden de empezar sesion");
	    					startNewSession();
	    				}
	    				break;
	    			case ControlHRPClient.TERMINAR_SESION:
	    				if (started)
	    				{
	    					logger.info("Recibida orden de finalizar sesion");
	    					endSession();
	    				}
	    				else
	    				{
	    					logger.info("Recibida orden de finalizar sesion, pero no estamos en sesion!");
	    					// No se está dando ninguna sesión.
	    				}
	    				break;
	    			case ControlHRPClient.PUBLICAR_ESTADO:
	    				logger.info("Recibida orden de publicar estado");
	    				byte[] estado_ = estado.toJSONString().getBytes();
	    		        if (publish(statusTopic, estado_))
	    		        	logger.info("Estado publicado!");
	    		        break;
	    			default:
	    				// Acción desconocida.
	    				break;
	    				
	    		}
	    	}
	    	catch (ParseException e){
	    		logger.error("Error al parsear mensaje:", e);
	    	}
	    	catch (Exception e) {
	    		logger.error("Mensaje de control mal formado. Excepcion:", e);
	    	}
    	}
    }
    
    @Override
    public void onMessageConfirmed(int messageId, String appTopic) {

    }

    @Override
    public void onMessagePublished(int messageId, String appTopic) {

    }
    
    // 
    
    /* MÉTODOS AUXILIARES */
    
    // 
    
    /* Método para iniciar una nueva sesión */ 
    private void startNewSession() throws ComponentException
    {
    	inicioSesion = new Date();
        
    	logger.info("Cliente HRP: Comenzando nueva sesion en dispositivo "+numDisp);
        
    	start();
    	if (started)
    	{
            String _inicioSesion = formatoFecha.format(inicioSesion);
            actualizarEstado(ControlHRPClient.ESTADO_EN_SESION, _inicioSesion, "");
    	}
    	else
    	{
    		logger.error("Error al iniciar nueva sesion");
    	}
     }
    
    /* Método para finalizar una sesión */
    private void endSession()
    {
    	logger.info("Cliente HRP: Finalizando sesion...");
        
    	stop();
    	
    	finSesion = new Date();
        
        int segundos = (int) (finSesion.getTime() - inicioSesion.getTime())/1000; 
        
        String duracion = secondsToString(segundos);
        
        logger.info("Cliente HRP: Sesion finalizada. Duracion: "+ duracion);
        String _inicioSesion = formatoFecha.format(inicioSesion);
        String _finSesion = formatoFecha.format(finSesion);
        actualizarEstado(ControlHRPClient.ESTADO_IDLE, _inicioSesion, _finSesion);
    }
    
    /* Método para iniciar el escaneo y lectura de dispositivos BLE para el pulso cardíaco. */
    private void start()
    {
    	logger.info("En start()...");
    	if (!dataService.isConnected())
    	{
    		logger.info("Conectando a la nube...");
    		try {
    			dataService.connect();
    		}
    		catch (KuraConnectException e)
    		{
    			logger.error("Error al conectar a la nube", e);
    		}
    	}
    	
        this.HRPDeviceList = new ArrayList<HRPDevice>();
        this.worker = Executors.newSingleThreadScheduledExecutor();

        logger.info("Obtener adaptador bluetooth...");
        try 
        {
        	// Obtener el adaptador bluetooth y comprobar que está activado
        	this.bluetoothAdapter = this.bluetoothService.getBluetoothAdapter(this.iname);
        	if (this.bluetoothAdapter != null) 
        	{
        		logger.info("Adaptador Bluetooth: "+iname+" -> "+bluetoothAdapter.getAddress() +
        					", LE activado: "+(bluetoothAdapter.isLeReady() ? "si" : "no"));
        		
        		if (!this.bluetoothAdapter.isEnabled()) 
        		{
        			logger.info("Habilitando adaptador...");
        			this.bluetoothAdapter.enable();
        		}	
        		this.startTime = 0;
        		this.connected = false;
        		// Cada segundo ejecutamos checkScan()
        		this.handle = this.worker.scheduleAtFixedRate(new Runnable() {
        			@Override
        			public void run() {
        				checkScan();
        			}
        		},
        				0, 1, TimeUnit.SECONDS);
        		
        		started = true;
        	} 	
        	else 
        	{
        		logger.warn("[!] Adaptador bluetooth no encontrado");
        	}
        } 
        catch (Exception e) 
        {
        	logger.error("Error al iniciar el componente ", e);
        	throw new ComponentException(e);
        }
    }
    
    /* Método para parar el escaneo y lectura de dispositivos BLE para el pulso cardíaco. */
    private void stop()
    {
    	started = false;
        // Paramos el escaneo si el adaptador está escaneando
        if (this.bluetoothAdapter != null && this.bluetoothAdapter.isScanning()) 
        {
            logger.debug("m_bluetoothAdapter.isScanning");
            this.bluetoothAdapter.killLeScan();
        }

        // Desconectamos todos lo dispositivos a los que nos habíamos conectado.
        for (HRPDevice device : this.HRPDeviceList) 
        {
            if (device != null) 
            {
            	logger.info("Desconectando de" +device.getAddress());
                device.disconnect();
            }
        }
        
        this.HRPDeviceList.clear();
        this.BlackList.clear();
        this.connectAttempts.clear();

        // Cancelamos el handle si estaba activo
        if (this.handle != null) 
        {
            this.handle.cancel(true);
        }

        // Shutdown al worker.
        if (this.worker != null) 
        {
            this.worker.shutdown();
        }

        // Cancelamos el adaptador bluetooth
        this.bluetoothAdapter = null;
        
    }
    
    // Método para imprimir en el log la configuración del bundle.
    private void logConfiguracion()
    {
    	logger.info("# ------------------------------------------------------------");
    	logger.info("# CONFIGURACION HRPCLIENT");
        logger.info("# Numero dispositivo: "+numDisp);
        logger.info("# Habilitado escaneo: "+ (client_enable ? "si" : "no"));
        logger.info("# Duracion escaneo: "+scantime+"s");
        logger.info("# Periodo escaneo: "+period+"s");
        logger.info("# Topic: "+topic);
        logger.info("# Adaptador bluetooth: "+iname);
        logger.info("#");
    }
    
    
    // Método para publicar datos en un topic.
    private static boolean publish(String topic, byte[] payload) 
    {
    	boolean published = false;
    	
    	if (dataService.isConnected())
    	{
            int qos = 1;
            boolean retain = false;
            int prioridad = 4;
            try 
            {
            	dataService.publish(topic, payload, qos, retain, prioridad);
            	published = true;
            } catch (Exception e) {
            	logger.error("Error en publishData():", e);
            }
    	}
    	else logger.error("Error en publishData(): Desconectado de la nube.");
    	
    	return published;
    }
        
 // Método para publicar en el topic de estado un nuevo estado.
    private void actualizarEstado(int _estado, String fechaIni, String fechaFin)
    {
    	logger.info("Actualizando estado");
    	
		this.estado = new JSONObject();

		this.estado.put("estado", _estado);
		this.estado.put("fechaIni", fechaIni);
		this.estado.put("fechaFin", fechaFin);

		byte[] payload = estado.toJSONString().getBytes();
        
        if (publish(statusTopic, payload))
        	logger.info("Estado publicado!");
    }
    
    // Método para suscribirse a un topic.
    private void suscribirTopic(String topic)
    {
    	int qos = 1;
    	try {
    		logger.info("Suscribir a "+topic+"...");
    		dataService.subscribe(topic, qos);
    	}
    	catch (KuraTimeoutException e) {
    		logger.error("Error al suscribir a "+topic+": TIMEOUT", e);
    	} catch (KuraNotConnectedException e) {
    		logger.error("Error al suscribir a "+topic+": NOT CONNECTED", e);
    	} catch (KuraException e) {
    		logger.error("Error al suscribir a "+topic, e);
    	}
    }
    
    /* Método para obtener una cadena del formato HHh MMm SSs*/
    public static String secondsToString(int seconds)
    {
        int horas = (int) Math.ceil(seconds/(60*60));
        seconds -= horas*60*60;
        int minutos = (int) Math.ceil(seconds/60);
        seconds -= minutos*60;

        String duracion = "";
        if (horas > 0)
            duracion += horas+"h ";
        if (minutos > 0 || horas > 0)
            duracion += minutos+"m ";
        duracion += seconds+"s";

        return duracion;
    }
    
    /* Método para leer y guardar las propiedades */
    private void readProperties(Map<String, Object> properties) 
    {
        if (properties != null) 
        {
            if (properties.get(this.PROPERTY_CODIGO_GIMNASIO) != null) {
                this.codigoGim = (String) properties.get(this.PROPERTY_CODIGO_GIMNASIO);
            }
            if (properties.get(this.PROPERTY_NUM_DISPOSITIVO) != null) {
                this.numDisp = (Integer) properties.get(this.PROPERTY_NUM_DISPOSITIVO);
            }
            if (properties.get(this.PROPERTY_ENABLE) != null) {
                this.client_enable = (Boolean) properties.get(this.PROPERTY_ENABLE);
            }
            if (properties.get(this.PROPERTY_SCANTIME) != null) {
                this.scantime = (Integer) properties.get(this.PROPERTY_SCANTIME);
            }
            if (properties.get(this.PROPERTY_PERIOD) != null) {
                this.period = (Integer) properties.get(this.PROPERTY_PERIOD);
            }
            if (properties.get(this.PROPERTY_INAME) != null) {
                this.iname = (String) properties.get(this.PROPERTY_INAME);
            }
        }
        // Formamos a partir del código del gimnasio y del número de dispositivo
        // los topics para un dispositivo en particular.
        
        // Topic donde publicará los datos
        topic = "fcoortbon/centralhrp/GIM_"+codigoGim+"/"+codigoGim+"_"+numDisp;
        // Topic de control en el que se recibirán órdenes
        controlTopic = topic + "/control";
        // Topic de estado donde el dispositivo publica su estado
        statusTopic = topic + "/status";
    }
    
    
    // Método para determinar si existe un dispositivo con una dirección
    // MAC dada en nuestra lista de dispositivos bluetooth.
    private boolean isInHRPDeviceList(String address) 
    {
        for (HRPDevice device : this.HRPDeviceList) 
        {
            if (device.getBluetoothDevice().getAdress().equals(address)) {
                return true;
            }
        }
        return false;
    }
    
     
    // Método para determinar si existe un dispositivo con una dirección
    // MAC dada en nuestra lista negra. (En esta lista se guardan las
    // direcciones de aquellos dispositivos a los que nos hemos conectado 
    // y no ofrecían el servicio de pulso cardíaco (HRS) y a aquellos para
    // los cuales nos hemos intentado conectar, sin éxito, el máximo número
    // de veces permitidas). 
    private boolean isInBlackList(String address)
    {
    	for (String adr : this.BlackList)
    	{
    		if (adr.equals(address)){
    			return true;
    		}
    	}
    	return false;
    }
    
    // 
    // Método para encontrar un dispositivo en nuestra lista de dispositivos 
    // bluetooth a partir de su dirección.
    private HRPDevice findInHRPDeviceList(String address) 
    {
        for (HRPDevice device : this.HRPDeviceList) 
        {
            if (device.getBluetoothDevice().getAdress().equals(address)) {
                return device;
            }
        }
        return null;
    }
        
}