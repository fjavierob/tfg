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

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.jjoe64.graphview.series.DataPoint;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**     INTERMEDIARIO
 *
 * @author fcoortbon
 *
 * Esta clase extiende el objeto Application, por lo que es un singleton que
 * se crea al iniciar la aplicación y los demás componentes pueden obtener
 * su instancia mediante el método getApplication().
 *
 * Este objeto es el que guarda los datos de la sesión en memoria e interacciona
 * con los demás componentes para mostrar la información correcta en el momento
 * adecuado. Si consideramos las entidades del patrón Modelo-Vista-Controlador,
 * este objeto sería tanto Modelo como Controlador.
 *
 * 1. Respecto a la actividad principal (MainActivity)
 *
 *    Actualiza su estado cada vez que el servicio ServicioSesion invoca al método
 *    actualizarEstado(int).
 *
 *    1.1. Respecto a GridFragment
 *
 *         Recordemos que la actividad principal contiene un fragmento con
 *         un GridView en el que cada elemento representa la última medida
 *         recibida de un dispositivo.
 *
 *         El objeto Intermediario se encarga de refrescar la información que
 *         se debe mostrar en esta vista cada vez que recibe nuevos datos. Para
 *         ello invoca al método updateGrid(ArrayList<GridDispositivo>) del
 *         GridFragment.
 *
 * 2. Respecto a la gráfica de medidas de un dispositivo (DialogGraficaFragment)
 *
 *    Recordemos que al hacer clic en un elemento del GridView, se muestra
 *    un diálogo con una gráfica en la que aparecen las medidas de pulso
 *    recibidas de ese dispositivo en concreto así como otros estadísticos.
 *
 *    El objeto Intermediario se encarga de proporcionar al diálogo de la gráfica,
 *    es decir, al DialogGraficaFragment en cuestión, los datos del dispositivo
 *    preciso (el objeto DialogGraficaFragment invoca el método
 *    getPuntos(String) del objeto Intermediario) así como de ir pasándole los
 *    nuevos datos relativos al dispositivo cuya gráfica se está mostrando
 *    para que la gráfica se actualice en tiempo real (para ello el objeto
 *    Intermediario invoca al método updateGraph(DataPoint[], int) del objeto
 *    DialogGraficaFragment).
 *
 *
 * Además, comprueba periódicamente la actividad de los dispositivos, y si se
 * advierte que no llegan datos de un dispositivo por más de X tiempo (12 segundos),
 * este se marca como inactivo.
 *
 */

public class Intermediario extends Application
{
    static final String LOG_TAG = Intermediario.class.getCanonicalName();

    static final int TIEMPO_INACTIVIDAD = 12*1000;

    private static Context myContext;

    private Handler myHandler;

    private MainActivity main;
    private GridFragment grid;
    private ArrayList<GridDispositivo> dispositivos;

    private int estado;

    private DialogGraficaFragment graficaFragment;
    private String graficaAddress;

    // Para cada dispositivo guardamos las medidas de pulso nos lleguen
    // en un array de puntos (tiempo(X), pulso(Y))
    HashMap<String, ArrayList<DataPoint>> pulsoDispositivos;
    // Guardamos el instante en el que llega cada última medida.
    HashMap<String, Date> actividadDispositivos;
    // Y también la energía gastada medida. Como es acumulativa, sólo
    // guardamos el último valor.
    HashMap<String, Integer> energiaDispositivos;

    // Marcamos con verdadero si queremos refrescar la información en el fragmento
    // con el grid de los dispositivos. Se marcará cuando llegue un nuevo dato o bien
    // cuando se advierta la inactividad de un dispositivo.
    public boolean wantToUpdateGridFragment;

    // Comprueba de forma periódica la actividad de los dispositivos comparando
    // el instante actual y el último instante en el que se recibió una medida
    // de cada dispositivo.
    // Además, aquí es desde donde se llama al método updateGridFragment() (dependiendo
    // del valor de la variable wantToUpdateGridFragment) para que no haya colisión
    // en su llamada.
    Runnable comprobarInactividad = new Runnable()
    {
        @Override
        public void run()
        {
            Date now = new Date();
            Iterator it = actividadDispositivos.entrySet().iterator();
            while (it.hasNext())
            {
                Map.Entry entrada = (Map.Entry) it.next();
                String address = (String) entrada.getKey();
                Date time = (Date) entrada.getValue();
                Log.d(LOG_TAG, "Comprobando dispositivo "+address+"...");
                if (now.getTime() - time.getTime() >= TIEMPO_INACTIVIDAD)
                {
                    Log.d(LOG_TAG, " * "+address+" INACTIVO");
                    GridDispositivo dispositivo = findInMyList(address);
                    if (dispositivo.isActive())
                    {
                        Log.d(LOG_TAG, "Marcando "+address+" como inactivo...");
                        dispositivo.setActive(false);
                        wantToUpdateGridFragment = true;
                    }
                }
            }
            // Refrescamos la información en el grid de dispositivos si se ha marcado
            // que se quiere refrescar.
            if (wantToUpdateGridFragment)
                updateGridFragment();
            myHandler.postDelayed(this, 500);
        }
    };

    public Intermediario()
    {
        super();

        myContext = this;

        myHandler = new Handler();

        main = null;
        grid = null;

        estado = ServicioSesion.CODIGO_DESCONECTADO;

        graficaFragment = null;

        dispositivos = new ArrayList<GridDispositivo>();
        pulsoDispositivos = new HashMap<String, ArrayList<DataPoint>>();
        energiaDispositivos = new HashMap<String, Integer>();
        actividadDispositivos = new HashMap<String, Date>();

        wantToUpdateGridFragment = false;
    }

    // Método estático para que quién necesite un contexto pueda obtenerlo.
    public static Context getContext()
    {
        return myContext;
    }

    // Método para limpiar toda la información en memoria.
    public void clear()
    {
        dispositivos.clear();
        pulsoDispositivos.clear();
        energiaDispositivos.clear();
        actividadDispositivos.clear();

        myHandler.removeCallbacks(comprobarInactividad);

        updateGridFragment();
    }

    /**
     * Método para obtener los puntos de la gráfica de un dispositivo.
     * @param address Dirección del dispositivo.
     * @return Array de puntos (eje X: tiempo, eje Y: medida de pulso).
     */
    public DataPoint[] getPuntos(String address)
    {
        if(pulsoDispositivos.containsKey(address))
            Log.d(LOG_TAG, "EXISTE "+address);
        else Log.d(LOG_TAG, "NO EXISTE "+address);
        return pulsoDispositivos.get(address).toArray(new DataPoint[pulsoDispositivos.size()]);
    }

    /**
     * Método para obtener la energía gastada acumulada medida por
     * un dispositivo.
     * @param address Dirección del dispositivo.
     * @return Valor de la energía gastada acumulada en kcal.
     */
    public int getEnergia(String address)
    {
        if (energiaDispositivos.containsKey(address))
            return energiaDispositivos.get(address);
        else
            return  -1;
    }

    /**
     * Método para obtener la referencia del GridFragment.
     * @param grid referencia del GridFragment activo.
     */
    public void setGridFragment(GridFragment grid)
    {
        this.grid = grid;
        wantToUpdateGridFragment = true;
    }

    // Método auxiliar para actualizar la información acerca de un
    // dispositivo. Almacena los nuevos datos, indica que está
    // activo y también indica que se quiere actualizar la información
    // del GridView.
    private void updateGridConDispositivo(GridDispositivo disp)
    {
        Log.d(LOG_TAG, "#updateGrid: "+disp.getAddress());

        GridDispositivo dispositivo = findInMyList(disp.getAddress());
        if (dispositivo != null)
        {
            Log.d(LOG_TAG, "Existe, actualizando...");
            dispositivo.setActive(true);
            dispositivo.setPulso(disp.getPulso());
            if (disp.getEnergia() != -1)
                dispositivo.setEnergia(disp.getEnergia());
            else
                dispositivo.setEnergia(getEnergia(disp.getAddress()));
        }
        else
        {
            Log.d(LOG_TAG, "No existe, insertar nuevo...");
            dispositivos.add(disp);
        }

        Log.d(LOG_TAG, "#updateGrid: "+dispositivos.size());

        // Indicamos que queremos actualizar la información del GridView.
        wantToUpdateGridFragment = true;
    }

    /**
     * Método para obtener la referencia de la actividad principal.
     * @param main referencia de la actividad principal.
     */
    public void setMainActivity(MainActivity main)
    {
        this.main = main;
        if (main != null)
            main.actualizarEstado(estado);
    }

    /**
     * Método que recibe nuevos datos de pulso. Será llamado por el servicio de sesión
     * cuando este reciba un nuevo mensaje de datos.
     * @param address Dirección del dispositivo.
     * @param puntoPulso Punto de pulso (eje X: tiempo, eje Y: medida de pulso).
     * @param energia Valor de la energía gastada acumulada. -1 si no hay nuevo valor.
     */
    public void nuevoDatoPulso(String address, DataPoint puntoPulso, int energia)
    {
        Date now = new Date();
        actividadDispositivos.put(address, now);

        ArrayList<DataPoint> datosPulso;
        if (pulsoDispositivos.containsKey(address))
        {
            // Si ya teníamos información del dispositivo, añadimos la nueva medida.
            datosPulso = pulsoDispositivos.get(address);
            datosPulso.add(puntoPulso);
        }
        else
        {
            // Si es un dispositivo del cual no teníamos información, añadimos
            // una nueva entrada en nuestro mapa pulsoDispositivos.
            datosPulso = new ArrayList<DataPoint>();
            datosPulso.add(puntoPulso);
            pulsoDispositivos.put(address, datosPulso);
        }

        // Actualizar valor de energía gastada
        if (energia != -1)
            energiaDispositivos.put(address, energia);

        GridDispositivo disp = new GridDispositivo(address, (int) puntoPulso.getY(), energia);
        // Método auxiliar para refrescar la información del dispositivo en cuestión.
        // Esto es, la información relativa al GridDispositivo de este dispositivo, es decir,
        // el dispositivo en tanto que elemento del GridView.
        updateGridConDispositivo(disp);

        // Si hay una gráfica siendo visualizada y llega un nuevo dato correspondiente
        // a esta gráfica, la actualizamos.
        if (graficaFragment != null && address.equals(graficaAddress))
            actualizarGrafica();
    }

    /**
     * Método que recibe un cambio de estado por parte del servicio y
     * a su vez actualiza la actividad principal con el nuevo estado.
     * @param codigo Código del estado.
     */
    public void actualizarEstado(int codigo)
    {
        estado = codigo;
        if (main != null)
            main.actualizarEstado(estado);
        if (estado == ServicioSesion.CODIGO_SESION_INICIADA)
        {
            // Si se indica que se acaba de iniciar una sesión, se
            // limpia toda la información anteriormente almacenada y
            // programamos la tarea de comprobar inactividad.
            clear();
            myHandler.postDelayed(comprobarInactividad, 1000);
        }
        else if (estado == ServicioSesion.CODIGO_SESION_FINALIZADA)
        {
            // Si se indica que se acaba de finalizar una sesión,
            // eliminamos el callback para la tarea de comprobar
            // inactividad.
            myHandler.removeCallbacks(comprobarInactividad);
        }
    }

    /**
     * Método para indicar si se está mostrando una gráfica al usuario y cuál es.
     * Será llamado por DialogGraficaFragment para indicar que se está mostrando.
     * Cuando se vaya a dejar de mostrar, deberá llamarse proporcionando valor null.
     * @param grafica Referencia del DialogGraficaFragment en pantalla.
     * @param address Dirección del dispositivo cuya gráfica se muestra.
     */
    public void setGrafica(DialogGraficaFragment grafica, String address)
    {
        graficaFragment = grafica;
        graficaAddress = address;
    }

    // Método auxiliar para actualizar la información del GridView
    private void updateGridFragment()
    {
        if (grid != null)
            grid.updateGrid(dispositivos);
        wantToUpdateGridFragment = false;
    }

    // Método auxiliar para actualizar la información de la gráfica que
    // se está mostrando.
    private void actualizarGrafica()
    {
        graficaFragment.updateGraph(getPuntos(graficaAddress), getEnergia(graficaAddress));
    }

    // Método auxiliar para encontrar un dispositivo en nuestra lista a partir
    // de su dirección. Un GridDispositivo.
    private GridDispositivo findInMyList(String address)
    {
        for (GridDispositivo dispositivo : dispositivos)
        {
            if (dispositivo.getAddress().equals(address))
                return dispositivo;
        }
        return null;
    }
}
