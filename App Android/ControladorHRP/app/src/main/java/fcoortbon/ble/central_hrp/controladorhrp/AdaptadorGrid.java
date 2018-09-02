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

/**
 * Created by Javi on 14/06/2017.
 */

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**     ADAPTADOR PARA EL GRIDVIEW DE LOS DISPOSITIVOS BLUETOOTH
 *
 * @author fcoortbon
 *
 * En esta clase se define un adaptador para rellenar el GridView
 * de los dispositivos bluetooth de los cuales estamos obteniendo
 * información a partir de una lista de objetos GridDispositivo.
 *
 */

public class AdaptadorGrid extends ArrayAdapter<GridDispositivo>
{

    private final List<GridDispositivo> mDispositivos;
    private final Context mContext;
    private final LayoutInflater mInflater;
    private int mResource;

    public AdaptadorGrid (Context context, int resourceId, ArrayList<GridDispositivo> dispositivos)
    {
        super(context, resourceId);

        mContext = context;
        mDispositivos = dispositivos;
        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mResource = resourceId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        // Una view por cada dispositivo bluetooth (GridDispositivo)
        // Sin embargo no creamos más views de las que caben en la pantalla.
        // Un elemento del grid que quede fuera, al hacerse visible obtendrá la
        // view de otro que haya quedado no visible.
        ViewHolder holder;
        if (convertView == null)
        {
            convertView = mInflater.inflate(mResource, null);
            holder = new ViewHolder();
            holder.address = (TextView)convertView.findViewById(R.id.gridDispositivoAddress);
            holder.pulso = (TextView)convertView.findViewById(R.id.gridDispositivoPulso);
            holder.energia = (TextView)convertView.findViewById(R.id.gridDispositivoEnergia);

            convertView.setTag(holder);
        }
        else
        {
            holder = (ViewHolder)convertView.getTag();
        }
        final GridDispositivo dispositivo = getItem(position);
        if (dispositivo != null)
        {
            holder.address.setText(dispositivo.getAddress());
            holder.pulso.setText(String.valueOf(dispositivo.getPulso()));
            int energia = dispositivo.getEnergia();
            if (energia == -1)
                holder.energia.setText("-");
            else
                holder.energia.setText(String.valueOf(dispositivo.getEnergia())+"kcal");

            if (!dispositivo.isActive())
            {
                // Si el dispositivo bluetooth está inactivo, ponemos su fondo de color gris.
                convertView.setBackgroundResource(R.drawable.grid_grey);
            }
            else
            {
                if (dispositivo.alerta(dispositivo.getPulso()))
                {
                    // Si el dispositivo bluetooth ha proporcionado una medida de pulso
                    // muy alta, ponemos el fondo en rojo.
                    convertView.setBackgroundResource(R.drawable.grid_red);
                }
                else
                {
                    // Si el dispositivo bluetooth ha proporcionado una medida de pulso
                    // que no es muy alta, ponemos el fondo en verde.
                    convertView.setBackgroundResource(R.drawable.grid_green);
                }
            }
        }
        return convertView;
    }

    @Override
    public int getCount()
    {
        return mDispositivos.size();
    }

    @Override
    public GridDispositivo getItem(int position)
    {
        return mDispositivos.get(position);
    }

    // Holder para aumentar la eficiencia al no tener que hacer continuamente
    // findViewById.
    public class ViewHolder {
        TextView address;
        TextView pulso;
        TextView energia;
    }
}
