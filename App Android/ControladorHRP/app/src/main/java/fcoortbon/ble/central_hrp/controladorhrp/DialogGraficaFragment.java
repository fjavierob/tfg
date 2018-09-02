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

import android.app.DialogFragment;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

import static java.lang.Math.floor;

/**     DIÁLOGO CON LA GRÁFICA DE DATOS DE UN DISPOSITIVO
 *
 * @author fcoortbon
 *
 * Esta clase consiste en un diálogo en el que se muestra una gráfica con
 * los datos de pulso recibidos de un dispositivo bluetooth concreto.
 * La gráfica se actualiza en tiempo real conforme lleguen nuevas medidas
 * desde el dispositivo bluetooth en cuestión.
 *
 * Además de la gráfica, se muestran los valores máximos y mínimos de pulso,
 * y los valores actuales de pulso y energía gastada acumulada.
 *
 * Ver layout dialog_grafica_fragment.xml
 *
 */

public class DialogGraficaFragment extends DialogFragment
{
    static final String LOG_TAG = DialogGraficaFragment.class.getCanonicalName();
    private LineGraphSeries<DataPoint> curva;
    Intermediario intermediario;
    GraphView graph;
    TextView tituloTv;
    TextView minTv;
    TextView maxTv;
    TextView pulsoTv;
    TextView energiaTv;

    // Creación de la vista.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             final Bundle savedInstanceState)
    {
        View rootView = inflater.inflate(R.layout.dialog_grafica_fragment, container, false);

        // En los argumentos recibiremos la dirección del dispositivo bluetooth.
        Bundle arguments = getArguments();
        String address = "default";
        if (arguments != null)
            address = arguments.getString("address");

        intermediario = (Intermediario) getActivity().getApplication();

        tituloTv = (TextView) rootView.findViewById(R.id.graphTitle);
        minTv = (TextView) rootView.findViewById(R.id.graphMinPulso);
        maxTv = (TextView) rootView.findViewById(R.id.graphMaxPulso);
        pulsoTv = (TextView) rootView.findViewById(R.id.graphPulso);
        energiaTv = (TextView) rootView.findViewById(R.id.graphEnergia);
        graph = (GraphView) rootView.findViewById(R.id.graph);

        // Obtenemos los puntos de la gráfica del dispositivo bluetooth en cuestión.
        DataPoint[] puntos = procesarPuntos(intermediario.getPuntos(address));

        // Creamos una nueva curva con los puntos.
        curva = new LineGraphSeries<>(puntos);
        // Activar marcado de puntos
        curva.setDrawDataPoints(true);
        // Establecemos un listener para que cuando se pulse en un punto se muestre
        // un Toast con la información de ese punto
        curva.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint)
            {
                int time = (int) dataPoint.getX();
                int pulso = (int) dataPoint.getY();
                int minutes = (int) floor(time/60);
                int seconds = time-60*minutes;
                Toast.makeText(getActivity(), minutes+":"+(seconds < 10 ? "0" : "")+seconds+"  "+pulso+"bpm", Toast.LENGTH_SHORT).show();
            }
        });
        // Limpiamos el GraphView
        graph.removeAllSeries();
        Log.d(LOG_TAG, "Puntos: "+puntos.length);

        // Añadimos nuestra curva al GraphView
        graph.addSeries(curva);

        tituloTv.setText(address);

        // Llamamos al método auxiliar updateGraph(DataPoint[], int)
        // para rellenar toda la información del diálogo.
        updateGraph(puntos, intermediario.getEnergia(address));

        // Establecemos la notación de los ejes.
        // En el eje Y mostraremos las medidas de pulso sin ningún formato especial.
        // En el eje X mostraremos el tiempo con el formato minutos:segundos.
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX)
                {
                    // Valores de pulso
                    int minutes = (int) floor(value/60);
                    int seconds = (int) value-60*minutes;
                    return super.formatLabel(minutes, isValueX) + ":"+(seconds < 10 ? "0" : "")+seconds;
                }
                else
                {
                    // Tiempo. Lo ponemos en formato 'minutos:segundos' -> 2:30
                    return super.formatLabel(value, isValueX);
                }
            }
        });

        // Pasamos nuestra referencia al intermediario para que pueda actualizar la información
        // conforme vayan llegando nuevos datos.
        intermediario.setGrafica(this, address);

        return rootView;
    }

    @Override
    public void onDestroyView()
    {
        // Notificamos al Intermediario de que nos destruimos.
        intermediario.setGrafica(null, "");
        super.onDestroyView();
    }

    // Función auxiliar para refrescar la información del diálogo.

    /**
     * Método para refrescar la información del diálogo de la gráfica.
     * @param _puntos Puntos de la gráfica
     * @param _energia Valor de la energía gastada acumulada.
     */
    public void updateGraph(DataPoint[] _puntos, int _energia)
    {
        DataPoint[] puntos = procesarPuntos(_puntos);

        // Actualizamos la gráfica con los nuevos puntos.
        curva.resetData(puntos);

        final int minX = (int) minX(puntos);
        final int maxX = (int) maxX(puntos);
        final int minY = (int) minY(puntos);
        final int maxY = (int) maxY(puntos);
        final int energia = _energia;
        // Última medida de pulso
        final int pulso = (int) puntos[puntos.length-1].getY();

        // Actualizamos los distintos campos de información.
        getActivity().runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                minTv.setText(String.valueOf(minY));
                maxTv.setText(String.valueOf(maxY));

                pulsoTv.setText(String.valueOf(pulso));
                if (GridDispositivo.alerta(pulso))
                    pulsoTv.setTextColor(Color.RED);
                else
                    pulsoTv.setTextColor(Color.BLACK);


                if (energia != -1)
                    energiaTv.setText(String.valueOf(energia)+"kcal");
                else
                    energiaTv.setText("-");
            }
        });

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(minY-10);
        graph.getViewport().setMaxY(maxY+10);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(minX > 2 ? minX-2 : 0);
        graph.getViewport().setMaxX(maxX+2);

        // enable scaling and scrolling
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);
    }

    @Override
    public void onResume()
    {
        // Establecer tamaño del diálogo
        ViewGroup.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        getDialog().getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        super.onResume();
    }

    // Función auxiliar para procesar el DataPoint[] que llega con valores nulos.
    private DataPoint[] procesarPuntos(DataPoint[] _puntos)
    {
        int tam = 0;
        for (int i = 0; i < _puntos.length; i++)
        {
            if (_puntos[i] != null)
                tam++;
        }
        DataPoint[] puntos = new DataPoint[tam];
        for (int i = 0; i < tam; i++)
        {
            puntos[i] = _puntos[i];
        }
        return puntos;
    }

    // Función auxiliar para obtener el valor de tiempo mínimo.
    private double minX(DataPoint [] puntos)
    {
        double min = 0;
        switch (puntos.length)
        {
            case 0:
                break;
            case 1:
                min = puntos[0].getX();
                break;
            default:
                min = puntos[0].getX();
                for (int i = 1; i<puntos.length; i++)
                {
                    min = puntos[i].getX() < min ? puntos[i].getX() : min;
                }
                break;
        }
        return min;
    }

    // Función auxiliar para obtener el valor de tiempo máximo.
    private double maxX(DataPoint [] puntos)
    {
        double max = 0;
        switch (puntos.length)
        {
            case 0:
                break;
            case 1:
                max = puntos[0].getX();
                break;
            default:
                max = puntos[0].getX();
                for (int i = 1; i<puntos.length; i++)
                {
                    max = puntos[i].getX() > max ? puntos[i].getX() : max;
                }
                break;
        }
        return max;
    }

    // Función auxiliar para obtener el valor de pulso mínimo.
    private double minY(DataPoint [] puntos)
    {
        double min = 0;
        switch (puntos.length)
        {
            case 0:
                break;
            case 1:
                min = puntos[0].getY();
                break;
            default:
                min = puntos[0].getY();
                for (int i = 1; i<puntos.length; i++)
                {
                    min = puntos[i].getY() < min ? puntos[i].getY() : min;
                }
                break;
        }
        return min;
    }

    // Función auxiliar para obtener el valor de pulso máximo.
    private double maxY(DataPoint [] puntos)
    {
        double max = 0;
        switch (puntos.length)
        {
            case 0:
                break;
            case 1:
                max = puntos[0].getY();
                break;
            default:
                max = puntos[0].getY();
                for (int i = 1; i<puntos.length; i++)
                {
                    max = puntos[i].getY() > max ? puntos[i].getY() : max;
                }
                break;
        }
        return max;
    }

}
