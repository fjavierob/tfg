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

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.regions.Region;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;

import java.security.KeyStore;
import java.util.UUID;

/**     ACTIVIDAD MAIN
 *
 * @author fcoortbon
 *
 * Actividad principal de la aplicación. Muestra un par de botones para iniciar y
 * finalizar una sesión en un dispositivo HRPClient.
 *
 * También contiene un Fragment que posee un GridView (ver layout activity_main.xml)
 * en el que irá apareciendo la última información recibida de cada dispositivo.
 * Al hacer clic en uno de ellos, se muestra una gráfica con todos los datos recogidos
 * y otras estadísticas.
 *
 * Los botones lo que hacen es poner en funcionamiento o parar el servicio
 * ServicioSesion, que es el que se encarga de conectar a la nube, controlar el
 * dispositivo HRPClient y recibir los datos.
 *
 * Los datos actualizados llegan a esta actividad mediante una clase intermedia
 * (Intermediario -> singleton del objecto Application) que es la encargada de
 * mantener los datos en memoria y actualizar la vista de esta actividad cuando
 * sea preciso.
 *
 */
public class MainActivity extends AppCompatActivity
{

    static final String LOG_TAG = MainActivity.class.getCanonicalName();

    private Intermediario intermediario;

    private Menu menu;

    private String gimnasio;
    private int numDisp;

    Messenger mServicio = null;
    boolean mBound;

    // Manejador de la conexión con nuestro servicio ServicioSesion
    private ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // Al conectarmos, obtenemos un Messenger para poder enviar
            // mensajes al servicio.
            mServicio = new Messenger(service);
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className)
        {
            mServicio = null;
            mBound = false;
        }
    };


    TextView tvStatus;

    AWSIotClient mIotAndroidClient;
    String clientId;
    String keystorePath;
    String keystoreName;
    String keystorePassword;

    Button btnStart;
    Button btnEnd;

    KeyStore clientKeyStore = null;
    String certificateId;

    CognitoCachingCredentialsProvider credentialsProvider;

    /**
     * Función que sirve para actualizar el estado (se muestra en un
     * TextView) y que es llamada por el objeto singleton Intermediario.
     *
     * Al recibir un nuevo código, se actualiza el TextView que muestra
     * el estado.
     *
     * @param codigo_ Codigo que identifica a un estado.
     */
    public void actualizarEstado(int codigo_)
    {
        final int codigo = codigo_;
        runOnUiThread(new Runnable() {
            @Override
            public void run()
            {
                switch (codigo) {
                    case ServicioSesion.CODIGO_CONECTANDO:
                        tvStatus.setText("Conectando...");
                        break;
                    case ServicioSesion.CODIGO_CONEXION_ESTABLECIDA:
                        tvStatus.setText("Conectado.");
                        break;
                    case ServicioSesion.CODIGO_DISPOSITIVO_DISPONIBLE:
                        tvStatus.setText("Conectado:\nDispositivo disponible.");
                        break;
                    case ServicioSesion.CODIGO_SESION_INICIADA:
                        tvStatus.setText("Conectado:\nEn sesion.");
                        break;
                    case ServicioSesion.CODIGO_FINALIZANDO_SESION:
                        tvStatus.setText("Conectado:\nFinalizando sesion...");
                        break;
                    case ServicioSesion.CODIGO_SESION_FINALIZADA:
                        tvStatus.setText("Desconectado:\nSesion finalizada.");
                        break;
                    case ServicioSesion.CODIGO_ERROR_CONECTAR:
                        tvStatus.setText("Desconectado:\nError al conectar a la nube.");
                        break;
                    case ServicioSesion.CODIGO_ERROR_SUSCRIBIR:
                        tvStatus.setText("Desconectado:\nError al suscribir.");
                        break;
                    case ServicioSesion.CODIGO_ERROR_DISPOSITIVO_TIMEOUT:
                        tvStatus.setText("Desconectado:\nDispositivo desconectado.");
                        break;
                    case ServicioSesion.CODIGO_ERROR_DISPOSITIVO_OCUPADO:
                        tvStatus.setText("Desconectado:\nDispositivo ocupado.");
                        break;
                    case ServicioSesion.CODIGO_ERROR_PUBLICAR:
                        tvStatus.setText("Desconectado:\nError al publicar.");
                        break;
                    case ServicioSesion.CODIGO_ERROR_INICIAR_SESION:
                        tvStatus.setText("Desconectado:\nError al iniciar nueva sesion en el dispositivo.");
                        break;
                    case ServicioSesion.CODIGO_ERROR_FINALIZAR_SESION:
                        tvStatus.setText("Conectado:\nError al finalizar sesion.");
                    case ServicioSesion.CODIGO_ERROR_DISPOSITIVO_TIMEOUT_FINALIZAR_SESION:
                        tvStatus.setText("Conectado:\n" +
                                "Error al finalizar sesion. No recibida respuesta del dispositivo");
                        break;
                    default:
                        Log.d(LOG_TAG, "#onReceive: Codigo de control desconocido: " + codigo);
                        break;
                }
            }
        });
    }

    /*
     * Función auxiliar usada para llevar a cabo el fin de una sesión.
     * Para ello le manda un mensaje al servicio ServicioSesion (si este
     * está activo y estamos enlazados a él) indicando que se quiere
     * finalizar la sesión que se está dando actualmente.
     *
     */
    private boolean finalizarSesion()
    {
        boolean result = false;

        if (mBound)
        {
            Message msg = Message.obtain(null, ServicioSesion.MSG_FINALIZAR_SESION, 0, 0);
            try
            {
                mServicio.send(msg);
                result = true;
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Sobreescribimos el método onDestroy para desenlazarnos
     * del servicio y actualizar a null el valor de la actividad
     * principal en el objeto singleton Intermediario antes de
     * que la actividad se destruya.
     */
    @Override
    protected void onDestroy()
    {
        Log.d(LOG_TAG, "# onDestroy");

        intermediario.setMainActivity(null);

        if (mBound)
            unbindService(mConnection);

        super.onDestroy();
    }

    /**
     * Sobreescribimos el método onCreate para poder inflar la vista de la
     * actividad, preparar los botones con sus acciones y para obtener las
     * credenciales que nos van a permitir conectarnos a la nube.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOG_TAG, "# onCreate");

        super.onCreate(savedInstanceState);

        // Inflamos la vista y obtenemos las referencias de los botones y
        // campos de texto.
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus_);

        btnStart = (Button) findViewById(R.id.btnStart_);
        btnStart.setOnClickListener(startClick);
        btnStart.setEnabled(false);

        btnEnd = (Button) findViewById(R.id.btnEnd_);
        btnEnd.setOnClickListener(endClick);

        // Obtenemos la instancia de nuestro singleton intermediario y le
        // pasamos nuestra referencia para que así pueda llamar a nuestro
        // método de actualizar estado.
        intermediario = (Intermediario) getApplication();
        intermediario.setMainActivity(this);

        // Asociarse al servicio si está en funcionamiento.
        if (isMyServiceRunning(ServicioSesion.class))
        {
            Log.d(LOG_TAG, "Bind al servicio...");
            bindService(new Intent(this, ServicioSesion.class), mConnection, 0);
        }

        // <--------------------------------------------------------------------------------
        //
        // Obtención de las credenciales para conectar a la nube
        //
        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        clientId = UUID.randomUUID().toString();

        // Initialize the AWS Cognito credentials provider
        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(), // context
                AWSConfig.COGNITO_POOL_ID, // Identity Pool ID
                AWSConfig.MY_REGION // Region
        );

        Region region = Region.getRegion(AWSConfig.MY_REGION);

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = new AWSIotClient(credentialsProvider);
        mIotAndroidClient.setRegion(region);

        keystorePath = getFilesDir().getPath();
        keystoreName = AWSConfig.KEYSTORE_NAME;
        keystorePassword = AWSConfig.KEYSTORE_PASSWORD;
        certificateId = AWSConfig.CERTIFICATE_ID;

        // To load cert/key from keystore on filesystem
        try
        {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                        keystoreName, keystorePassword))
                {
                    Log.i(LOG_TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword);
                    btnStart.setEnabled(true);
                } else {
                    Log.i(LOG_TAG, "Key/cert " + certificateId + " not found in keystore.");
                }
            } else {
                Log.i(LOG_TAG, "Keystore " + keystorePath + "/" + keystoreName + " not found.");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "An error occurred retrieving cert/key from keystore.", e);
        }

        if (clientKeyStore == null) {
            Log.i(LOG_TAG, "Cert/key was not found in keystore - creating new key and certificate.");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Create a new private key and certificate. This call
                        // creates both on the server and returns them to the
                        // device.
                        CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                                new CreateKeysAndCertificateRequest();
                        createKeysAndCertificateRequest.setSetAsActive(true);
                        final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                        createKeysAndCertificateResult =
                                mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                        Log.i(LOG_TAG,
                                "Cert ID: " +
                                        createKeysAndCertificateResult.getCertificateId() +
                                        " created.");

                        // store in keystore for use in MQTT client
                        // saved as alias "default" so a new certificate isn't
                        // generated each run of this application
                        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                                createKeysAndCertificateResult.getCertificatePem(),
                                createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                                keystorePath, keystoreName, keystorePassword);

                        // load keystore from file into memory to pass on
                        // connection
                        clientKeyStore = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                                keystorePath, keystoreName, keystorePassword);

                        // Attach a policy to the newly created certificate.
                        // This flow assumes the policy was already created in
                        // AWS IoT and we are now just attaching it to the
                        // certificate.
                        AttachPrincipalPolicyRequest policyAttachRequest =
                                new AttachPrincipalPolicyRequest();
                        policyAttachRequest.setPolicyName(AWSConfig.AWS_IOT_POLICY_NAME);
                        policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                                .getCertificateArn());
                        mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnStart.setEnabled(true);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(LOG_TAG,
                                "Exception occurred when generating new private key and certificate.",
                                e);
                    }
                }
            }).start();
        }

        // Fin credenciales.
        //  -------------------------------------------------------------------------------->
    }

    // Método auxiliar para comprobar si un servicio está activo.
    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // Listener para el botón de comenzar sesión
    View.OnClickListener startClick = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            Log.d(LOG_TAG, "#startClick ");

            // Obtenemos de las preferencias el código del gimnasio y el número del dispositivo.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            gimnasio = prefs.getString(getResources().getString(R.string.pref_gimnasio_key), "");
            numDisp = Integer.parseInt(prefs.getString(getResources().getString(R.string.pref_dispositivo_key), "1"));

            if (gimnasio.equals(""))
            {
                // Si no se ha configurado ningún gimnasio, no se hace nada.
                Toast.makeText(getBaseContext(), "No ha configurado ningún gimnasio", Toast.LENGTH_LONG).show();
            }
            else
            {
                if (isMyServiceRunning(ServicioSesion.class))
                {
                    // Si el servicio ya está activo es porque ya se está dando una sesión.
                    Log.d(LOG_TAG, "Servicio ya iniciado!");
                }
                else
                {
                    // Comenzamos nueva sesión.
                    dialogoIniciarSesion();
                }
            }
        }
    };

    // Listener para el botón de finalizar sesión
    View.OnClickListener endClick = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            Log.d(LOG_TAG, "#endClick ");
            if (isMyServiceRunning(ServicioSesion.class))
            {
                // Si el servicio está activo, finalizamos la sesión.
                finalizarSesion();
            }
        }
    };

    // Diálogo auxiliar para el inicio de sesión.
    // Pide confirmación, y en caso positivo, se inicia la sesión.
    private void dialogoIniciarSesion()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder
                .setMessage("¿Iniciar nueva sesion en dispositivo "+numDisp+"?")
                .setPositiveButton("Si",  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        // En caso positivo, iniciamos la sesión.
                        // Para ello iniciamos el servicio ServicioSesion pasándole
                        // los parámetros necesarios.
                        Intent intent = new Intent(getBaseContext(), ServicioSesion.class);
                        intent.putExtra("gimnasio", gimnasio);
                        intent.putExtra("num_dispositivo", numDisp);
                        intent.putExtra("clientId", clientId);
                        intent.putExtra("certificateId", certificateId);
                        intent.putExtra("keystoreName", keystoreName);
                        intent.putExtra("keystorePath", keystorePath);
                        intent.putExtra("keystorePassword", keystorePassword);

                        startService(intent);
                        Log.d(LOG_TAG, "Bind al servicio...");
                        bindService(new Intent(getBaseContext(), ServicioSesion.class), mConnection, 0);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,int id)
                    {
                        dialog.cancel();
                    }
                })
                .show();
    }

    // Listener para los botones de la barra de herramientas.
    // Esta barra sólo posee un botón que sirve para viajar
    // a la actividad de las preferencias.
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Log.d(LOG_TAG, "#onOptionsItemSelected");
        switch (item.getItemId())
        {
            // Botón de opciones
            case R.id.botonOpciones:
                Intent intent = new Intent(this, OpcionesActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Método para la creación de la barra de herramientas.
    // Inflamos su vista a partir del fichero xml menu.
    public boolean onCreateOptionsMenu(Menu menu)
    {
        this.menu = menu;
        Log.d(LOG_TAG, "#onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

}
