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

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import java.util.ArrayList;

/**     GRID FRAGMENT
 *
 * @author fcoortbon
 *
 * Fragment que contiene una GridView en la que se muestran los dispositivos
 * de los cuales se está recibiendo información junto con las últimas medidas
 * recibidas de ellos.
 *
 * Al clicar en uno de los elementos (dispositivo) se muestra una gráfica con
 * todos los datos recibidos hasta el momento del dispositivo en cuestión y que
 * se actualiza en tiempo real.
 *
 */

public class GridFragment extends Fragment
{
    static final String LOG_TAG = GridFragment.class.getCanonicalName();

    Intermediario intermediario;

    private View rootView;
    private GridView gridView;
    ArrayList<GridDispositivo> dispositivos;
    AdaptadorGrid adaptador;

    // Listener para el clic en uno de los elementos
    AdapterView.OnItemClickListener dispositivoClick = new AdapterView.OnItemClickListener()
    {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {
            GridDispositivo dispositivo = adaptador.getItem(position);
            Log.d(LOG_TAG, "Clic en "+dispositivo.getAddress());

            // Mostramos una gráfica con los datos del dispositivo que se ha pulsado
            // (identificado por su dirección).
            DialogGraficaFragment grafica = new DialogGraficaFragment();
            Bundle argumentos = new Bundle();
            argumentos.putString("address", dispositivo.getAddress());
            grafica.setArguments(argumentos);
            grafica.show(getFragmentManager(), "Fragment grafica");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOG_TAG, "Estado: Create.");
        super.onCreate(savedInstanceState);
    }

    public void onActivityCreated(Bundle savedInstanceState)
    {
        Log.d(LOG_TAG, "onActivityCreated # ");
        super.onActivityCreated(savedInstanceState);
    }

    // Creación de la vista.
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        Log.d(LOG_TAG, "Creando view...");
        rootView = inflater.inflate(R.layout.grid_fragment, container, false);

        gridView = (GridView) rootView.findViewById(R.id.gridDispositivos);

        gridView.setOnItemClickListener(dispositivoClick);

        // Creamos la GridView vacía
        dispositivos = new ArrayList<>();
        adaptador = new AdaptadorGrid(getActivity(), R.layout.grid_dispositivo, dispositivos);
        gridView.setAdapter(adaptador);

        // Pasamos al Intermediario nuestra referencia. De esta forma él llamará
        // a nuestro método updateGrid(ArrayList<GridDispositivo>) para dotarnos
        // de contenido.
        intermediario = (Intermediario) getActivity().getApplication();
        intermediario.setGridFragment(this);

        return rootView;
    }

    @Override
    public void onDestroyView()
    {
        // Hacemos saber al Intermediario que nos destruimos.
        intermediario.setGridFragment(null);
        super.onDestroyView();
    }

    /**
     * Método para actualizar la GridView. Será llamado por el Intermediario
     * cuando sea preciso actualizar la información de la GridView.
     * @param dispositivos Lista de dispositivos.
     */
    public void updateGrid(ArrayList<GridDispositivo> dispositivos)
    {
        Log.d(LOG_TAG, "#updateGrid: "+dispositivos.size());
        final ArrayList<GridDispositivo> disps = dispositivos;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adaptador = new AdaptadorGrid(getActivity(), R.layout.grid_dispositivo, disps);
                gridView.setAdapter(adaptador);
            }
        });

    }

}

