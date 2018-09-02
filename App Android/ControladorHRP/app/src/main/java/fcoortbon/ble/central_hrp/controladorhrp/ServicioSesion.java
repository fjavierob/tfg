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

package fcoortbon.ble.central_hrp.controladorhrp;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttLastWillAndTestament;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.jjoe64.graphview.series.DataPoint;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.util.Date;

import fcoortbon.ble.central_hrp.ControlHRPClient;

import static java.lang.Math.round;

/**     SERVICIO SESIÓN
 *
 * @author fcoortbon
 *
 * Mediante el uso de este servicio, la aplicación se conecta a la nube (usando
 * la configuración indicada en la clase AWSConfig) y controla un dispositivo
 * HRPClient: envía órdenes para iniciar y finalizar sesiones y recibe los datos
 * que este envía. Para ello, ambos publican en topics que ambos conocen y es el broker
 * MQTT de Amazon el que se encarga de recibir los mensajes y enviarlos a los
 * suscriptores.
 *
 */

public class ServicioSesion extends Service
{
    static final String LOG_TAG = ServicioSesion.class.getCanonicalName();

    static final int MSG_FINALIZAR_SESION = 1;

    static final double FACTOR_KJ_TO_KCAL = 0.239006;

    // Handler para recibir los mensajes de la actividad principal.
    // Sólo se considera uno: El mensaje que le envía la activdad
    // principal al servicio para indicar que quiere finalizar la sesión.
    class IncomingHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case MSG_FINALIZAR_SESION:
                    finalizarSesion();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    final Messenger messenger = new Messenger(new IncomingHandler());

    private Intermediario intermediario;

    public static final String DATOS_HRM = "Datos hrm";

    // Valor del timeout en ms para las respuestas del dispositivo.
    private static final int TIMEOUT = 3000;

    // Códigos de estado
    public static final int CODIGO_DESCONECTADO = 0;

    public static final int CODIGO_ERROR_CONECTAR = 1;
    public static final int CODIGO_ERROR_SUSCRIBIR = 2;
    public static final int CODIGO_ERROR_DISPOSITIVO_TIMEOUT = 3;
    public static final int CODIGO_ERROR_DISPOSITIVO_OCUPADO = 4;
    public static final int CODIGO_ERROR_PUBLICAR = 5;
    public static final int CODIGO_ERROR_INICIAR_SESION = 6;
    public static final int CODIGO_ERROR_FINALIZAR_SESION = 7;
    public static final int CODIGO_ERROR_DISPOSITIVO_TIMEOUT_FINALIZAR_SESION = 8;

    public static final int CODIGO_CONECTANDO = 30;
    public static final int CODIGO_CONEXION_ESTABLECIDA = 31;
    public static final int CODIGO_DISPOSITIVO_DISPONIBLE = 32;
    public static final int CODIGO_SESION_INICIADA = 33;
    public static final int CODIGO_FINALIZANDO_SESION = 34;
    public static final int CODIGO_SESION_FINALIZADA = 35;

    // Guardamos el instante en el que se inicia una nueva sesión.
    // Luego cuando lleguen los datos de pulso, veremos la diferencia
    // para calcular los valores de tiempo de cada mensaje.
    private Date fechaInicio;

    private int codigoTimeout;

    private Handler myHandler;

    // Callback para la llegada de los mensajes MQTT desde la nube
    private AWSIotMqttNewMessageCallback newMqttMessage = new AWSIotMqttNewMessageCallback()
    {
        @Override
        public void onMessageArrived(final String topic, final byte[] data)
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                   if (topic.equals(datosTopic))
                   {
                       // Llega un nuevo mensaje de datos.
                       try
                       {
                           String message = new String(data, "UTF-8");
                           Log.d(LOG_TAG, "Message arrived: "+message);
                           // Lo procesamos usando una función auxiliar.
                           procesarMensajeDatos(message);
                       }
                       catch (UnsupportedEncodingException e)
                       {
                           Log.e(LOG_TAG, "Message encoding error.", e);
                       }
                   }
                   else if (topic.equals(statusTopic))
                   {
                       // Llega un mensaje de un dispositivo comunicando su estado.
                       if (esperandoEstadoDispositivo)
                       {
                           // Si estabamos esperando un mensaje de este tipo del dispositivo:
                           // esto es cuando queremos iniciar una sesión, primero se comprueba
                           // que el dispositivo está disponible.
                           Log.d(LOG_TAG, "Recibido estado de dispositivo: cancelando callback para el timeout...");
                           myHandler.removeCallbacks(timeoutRunnable);
                           esperandoEstadoDispositivo = false;
                           try
                           {
                               String message = new String(data, "UTF-8");
                               JSONObject messageJSON = new JSONObject(message);
                               // Obtenemos el estado del dispositivo y
                               // actuamos en consecuencia.
                               switch (messageJSON.getInt("estado"))
                               {
                                   case ControlHRPClient.ESTADO_EN_SESION:
                                       Log.d(LOG_TAG, "El dispositivo ya se encuentra en una sesion");
                                       actualizarEstado(CODIGO_ERROR_DISPOSITIVO_OCUPADO);
                                       actualizarEstadoDc = false;
                                       stopSelf();
                                       break;
                                   case ControlHRPClient.ESTADO_IDLE:
                                       Log.d(LOG_TAG, "Dispositivo disponible para iniciar nueva sesion");
                                       actualizarEstado(CODIGO_DISPOSITIVO_DISPONIBLE);
                                       suscribirDatosTopic();
                                       iniciarNuevaSesion();
                                       break;
                                   default:
                                       Log.d(LOG_TAG, "Estado desconocido del dispositivo");
                                       actualizarEstado(CODIGO_ERROR_DISPOSITIVO_OCUPADO);
                                       stopSelf();
                                       break;
                               }
                           }
                           catch (UnsupportedEncodingException e)
                           {
                               Log.e(LOG_TAG, "Message encoding error.", e);
                           }
                           catch (JSONException e)
                           {
                               Log.e(LOG_TAG, "Excepcion JSON", e);
                           }
                       }
                       else if (esperandoInicioNuevaSesion)
                       {
                           // Si estamos esperando la confirmación por parte del dispositivo del
                           // inicio de una nueva sesión. Esto es, tras ver que el dispositivo estaba
                           // disponible (nos respondió publicando su estado anteriormente) se le
                           // envió la orden de empezar una nueva sesión. Cuando el dispositivo
                           // recibe esta orden, empieza una nueva sesión y actualiza su estado
                           // indicando que está en sesión.
                           Log.d(LOG_TAG, "Recibido estado de dispositivo: Sesion iniciada!");
                           myHandler.removeCallbacks(timeoutRunnable);
                           esperandoInicioNuevaSesion = false;
                           enSesion = true;
                           fechaInicio = new Date();
                           actualizarEstado(CODIGO_SESION_INICIADA);
                       }
                       else if (esperandoFinSesion)
                       {
                           // Similar al caso anterior. Ahora le enviamos la orden de finalizar
                           // la sesión y el dispositivo al recibirla y finalizarla, publicará
                           // su nuevo estado.
                           Log.d(LOG_TAG, "Recibido estado de dispositivo: Sesion finalizada!");
                           myHandler.removeCallbacks(timeoutRunnable);
                           esperandoFinSesion = false;
                           actualizarEstado(CODIGO_SESION_FINALIZADA);
                           actualizarEstadoDc = false;
                           stopSelf();
                       }
                   }
                   else
                   {
                       Log.d(LOG_TAG, "Recibido mensaje en topic desconocido: "+topic);
                   }
                }
            }).start();
        }
    };

    // Runnable para los timeout.
    private Runnable timeoutRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            Log.d(LOG_TAG, "Alcanzado timeout...");
            actualizarEstado(codigoTimeout);
            actualizarEstadoDc = false;
            if (codigoTimeout != CODIGO_ERROR_DISPOSITIVO_TIMEOUT_FINALIZAR_SESION)
                stopSelf();
            // ¿qué hacer si el dispositivo no finaliza su sesión (su nuevo estado no llega)?
        }
    };

    AWSIotMqttManager mqttManager;

    boolean connected;
    boolean onSession;
    boolean subscribedDatos;
    boolean subscribedControl;

    private String datosTopic;
    private String statusTopic;
    private String controlTopic;

    private boolean esperandoEstadoDispositivo = false;
    private boolean esperandoInicioNuevaSesion = false;
    private boolean esperandoFinSesion = false;
    private boolean enSesion = false;
    private boolean actualizarEstadoDc = true;

    /**
     * Método para que una actividad se asocie al servicio y obtena un
     * Messenger a partir del cual podrá enviar mensajes al servicio.
     * @param intent Intent para el enlace de la actividad al servicio.
     * @return Messenger para la comunicación de la activdad con el
     * servicio.
     */
    @Override
    public IBinder onBind(Intent intent)
    {
        Log.d(LOG_TAG, "Enlazando servicio con actividad...");
        return messenger.getBinder();
    }

    @Override
    public void onCreate()
    {
        this.connected = false;
        this.onSession = false;
        this.subscribedDatos = false;
        this.subscribedControl = false;
        this.intermediario = (Intermediario) getApplication();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {

        /* Comenzar una nueva sesion en el dispositivo:
         *
         *   1. Conectar a la nube.
         *
         *   2. Suscribir al topic de estado del dispositivo y enviar al
         *      topic de control de dispositivo un mensaje indicando que
         *      queremos que publique su estado. De esta forma comprobamos
         *      que el dispositivo está funcionando y si está disponible o no.
         *
         *   3. En caso de estar el dispositivo disponible, enviarle la orden
         *      de comenzar una nueva sesión: publicar en el topic de control
         *      del dispositivo que se quiere empezar una nueva sesión.
         *
         *   4. Al llegar los datos, notificamos a nuestro singleton Intermedio
         *      con los nuevos datos para que los almacene y procese.
         *
         * En caso de darse algún error en alguno de los pasos, se notifica
         * llamando al método actualizarEstado del objeto Intermedio.
         *
         */

        myHandler = new Handler(Looper.getMainLooper());

        // Obtenemos los parámetros del intent.
        Bundle params = intent.getExtras();
        String codigoGim = params.getString("gimnasio");
        int numDisp = params.getInt("num_dispositivo");
        String clientId = params.getString("clientId");
        String certificateId = params.getString("certificateId");
        String keystorePath = params.getString("keystorePath");
        String keystoreName = params.getString("keystoreName");
        String keystorePassword = params.getString("keystorePassword");
        KeyStore clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                keystorePath, keystoreName, keystorePassword);

        // Formamos los topics
        datosTopic = "fcoortbon/centralhrp/GIM_"+codigoGim+"/"+codigoGim+"_"+numDisp;
        statusTopic = datosTopic+"/status";
        controlTopic = datosTopic+"/control";

        Log.d(LOG_TAG, "Topic: "+datosTopic);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, AWSConfig.CUSTOMER_SPECIFIC_ENDPOINT);

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        // De momento lo dejo así. Puede que en el futuro le encuentre una utilidad a este
        // mensaje.
        AWSIotMqttLastWillAndTestament lwt = new AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0);
        mqttManager.setMqttLastWillAndTestament(lwt);



        // Establecer conexión con la nube.
        try
        {
            mqttManager.connect(clientKeyStore, new AWSIotMqttClientStatusCallback()
            {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable)
                {
                    // Recibimos cambio de estado en la conexión.
                    Log.d(LOG_TAG, "Status = " + String.valueOf(status));

                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            // Actuamos en consecuencia según qué estado.
                            if (status == AWSIotMqttClientStatus.Connecting)
                            {
                                Log.d(LOG_TAG, "#connect: Connecting...");
                                connected = false;
                                actualizarEstado(CODIGO_CONECTANDO);
                            }
                            else if (status == AWSIotMqttClientStatus.Connected)
                            {
                                Log.d(LOG_TAG, "#connect: Connected.");
                                connected = true;
                                actualizarEstado(CODIGO_CONEXION_ESTABLECIDA);
                            }
                            else if (status == AWSIotMqttClientStatus.Reconnecting)
                            {
                                if (throwable != null)
                                {
                                    Log.e(LOG_TAG, "#connect: Connection error.", throwable);
                                }
                                Log.d(LOG_TAG, "#connect: Reconnecting...");
                                connected = false;
                                actualizarEstadoDc = true;
                            }
                            else if (status == AWSIotMqttClientStatus.ConnectionLost)
                            {
                                if (throwable != null)
                                {
                                    Log.e(LOG_TAG, "#connect: Connection error.", throwable);
                                }
                                Log.d(LOG_TAG, "#connect: Disconnected.");
                                connected = false;
                                // Es posible que ya hubiesemos actualizado el estado con un
                                // código de error y que a su vez ese código de error llevase
                                // a este estado. En tal caso, se habría establecido a falso
                                // la variable actualizarEstadoDc, indicando que no queremos
                                // actualizar este estado.
                                if (actualizarEstadoDc) actualizarEstado(CODIGO_ERROR_CONECTAR);
                                stopSelf();
                            }
                            else
                            {
                                Log.d(LOG_TAG, "#connect: Disconnected.");
                                connected = false;
                                actualizarEstado(CODIGO_ERROR_CONECTAR);
                                actualizarEstadoDc = false;
                                stopSelf();
                            }
                            if (connected) consultarDispositivo();
                        }
                    }).start();
                }
            });
        }
        catch (final Exception e)
        {
            Log.e(LOG_TAG, "#connect: Connection error.", e);
            connected = false;
            actualizarEstado(CODIGO_ERROR_CONECTAR);
            actualizarEstadoDc = false;
            stopSelf();
        }

        // Si android mata el servicio por falta de memoria, no queremos que
        // intente recrearlo cuando la tenga.
        return START_NOT_STICKY;
    }

    // Al destruir el servicio, nos desuscribimos de los topics y nos desconectamos
    // de la nube
    @Override
    public void onDestroy()
    {
        if (connected)
        {
            if (enSesion)
                if (subscribedDatos)
                {
                    Log.d(LOG_TAG, "#onDestroy: Desuscribiendo del topic de datos...");
                    mqttManager.unsubscribeTopic(datosTopic);
                    mqttManager.unsubscribeTopic(controlTopic);
                }
            if (subscribedControl)
            {
                Log.d(LOG_TAG, "#onDestroy: Desuscribiendo del topic de control...");
                mqttManager.unsubscribeTopic(controlTopic);
            }
            Log.d(LOG_TAG, "#onDestroy: Desconectando...");
            mqttManager.disconnect();
        }
        super.onDestroy();
    }

    // Función auxiliar que se llama al recibir un mensaje de datos.
    // Extrae la información de pulso, dispositivo y energía, obtiene el
    // tiempo respecto al inicio de sesión y pasa estos datos al singleton
    // Intermediario.
    private void procesarMensajeDatos(String message)
    {
        try
        {
            JSONObject messageJSON = new JSONObject(message);

            String address = messageJSON.getString("address");
            int pulso = messageJSON.getInt("pulso");
            int energia = -1;
            if (messageJSON.has("energia"))
                energia = kJtokcal(messageJSON.getInt("energia"));

            Date now = new Date();
            int time = (int) (now.getTime() - fechaInicio.getTime())/1000;
            // Guardar dato de pulso
            DataPoint puntoPulso = new DataPoint(time, pulso);
            intermediario.nuevoDatoPulso(address, puntoPulso, energia);
        }
        catch (JSONException e)
        {
            Log.e(LOG_TAG, "Excepcion JSON", e);
        }
    }

    // Función auxiliar para pasar de kJ a kcal.
    private int kJtokcal(int kJ)
    {
        return (int) round(kJ*FACTOR_KJ_TO_KCAL);
    }

    // Función auxiliar para difundir datos.
    private void bcastDatos(String datos)
    {
        Log.d(LOG_TAG, "Difundir datos...");
        Intent intent = new Intent(DATOS_HRM);
        intent.putExtra("datos", datos);
        sendBroadcast(intent);
    }

    // Función auxiliar para actualizar el estado.
    private void actualizarEstado(int codigo)
    {
        Log.d(LOG_TAG, "Actualizar estado. Codigo: "+codigo+"...");
        intermediario.actualizarEstado(codigo);
    }

    // Función auxiliar para consultar el dispositivo.
    // Envía un mensaje al topic de control del dispositivo en
    // cuestión ordenándole que publique su estado y programa
    // un timeout para la respuesta del dispositivo.
    private void consultarDispositivo()
    {
        Log.d(LOG_TAG, "Consultar dispositivo...");

        try
        {
            // Nos suscribimos al topic de estado del dispositivo
            mqttManager.subscribeToTopic(statusTopic, AWSIotMqttQos.QOS0, newMqttMessage);
            subscribedControl = true;
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "Subscription error.", e);
            subscribedControl = false;
            actualizarEstado(CODIGO_ERROR_SUSCRIBIR);
            actualizarEstadoDc = false;
            stopSelf();
        }

        // Publicamos en el topic de control un mensaje pidiendo al dispositivo que
        // publique su estado
        try
        {
            Log.d(LOG_TAG, "Publicar en topic de control: pedir estado del dispositivo");
            JSONObject msg = new JSONObject();
            msg.put("action", ControlHRPClient.PUBLICAR_ESTADO);
            mqttManager.publishString(msg.toString(), controlTopic, AWSIotMqttQos.QOS0);
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "Publish error.", e);
            actualizarEstado(CODIGO_ERROR_PUBLICAR);
            actualizarEstadoDc = false;
            stopSelf();
        }

        // Programamos un timeout. Cancelaremos el timeout si recibimos el mensaje a tiempo.
        codigoTimeout = CODIGO_ERROR_DISPOSITIVO_TIMEOUT;
        myHandler.postDelayed(timeoutRunnable, TIMEOUT);
        esperandoEstadoDispositivo = true;
    }

    // Método auxiliar para suscribirse al topic de datos del dispositivo.
    private void suscribirDatosTopic()
    {
        Log.d(LOG_TAG, "Suscribir a topic de datos " + datosTopic +"...");

        try
        {
            mqttManager.subscribeToTopic(datosTopic, AWSIotMqttQos.QOS0, newMqttMessage);
            subscribedDatos = true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Subscription error.", e);
            subscribedDatos = false;
        }
    }

    // Método auxiliar para el inicio de una nueva sesión.
    // Envía al topic de control del dispositivo en cuestión un
    // mensaje ordenándole que inicie una nueva sesión y programa
    // un timeout para la respuesta del dispositivo.
    private void iniciarNuevaSesion()
    {
        try
        {
            Log.d(LOG_TAG, "Publicar en topic de control: ordenar inicio de nueva sesion");
            JSONObject msg = new JSONObject();
            msg.put("action", ControlHRPClient.COMENZAR_SESION);
            mqttManager.publishString(msg.toString(), controlTopic, AWSIotMqttQos.QOS0);
            esperandoInicioNuevaSesion = true;
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "Publish error.", e);
            actualizarEstado(CODIGO_ERROR_PUBLICAR);
            actualizarEstadoDc = false;
            stopSelf();
        }

        // Programamos un timeout. Cancelaremos el timeout si recibimos el mensaje a tiempo.
        codigoTimeout = CODIGO_ERROR_INICIAR_SESION;
        myHandler.postDelayed(timeoutRunnable, TIMEOUT);
        esperandoInicioNuevaSesion = true;
    }

    // Método auxiliar para finalizar una sesión en el dispositivo.
    // Envía al topic de control del dispositivo en cuestión un
    // mensaje ordenándole que finalice la sesión y programa
    // un timeout para la respuesta del dispositivo.
    private void finalizarSesion()
    {
        boolean result = false;
        if (enSesion)
        {
            try
            {
                Log.d(LOG_TAG, "Publicar en topic de control: finalizar sesion");
                JSONObject msg = new JSONObject();
                msg.put("action", ControlHRPClient.TERMINAR_SESION);
                mqttManager.publishString(msg.toString(), controlTopic, AWSIotMqttQos.QOS0);

                // Programamos un timeout para la respuesta del dispositivo (este responderá
                // actualizando su estado a idle.
                codigoTimeout = CODIGO_ERROR_DISPOSITIVO_TIMEOUT_FINALIZAR_SESION;
                myHandler.postDelayed(timeoutRunnable, TIMEOUT);
                esperandoFinSesion = true;

                result = true;
            }
            catch (Exception e)
            {
                Log.e(LOG_TAG, "Error al publicar fin sesion", e);
            }
        }
        if (result)
        {
            actualizarEstado(CODIGO_FINALIZANDO_SESION);
        }
        else actualizarEstado(CODIGO_ERROR_FINALIZAR_SESION);
    }

}
