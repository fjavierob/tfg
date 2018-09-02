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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**     GRID DISPOSITIVO
 *
 * @author fcoortbon
 *
 * Esta clase representa los dispositivos bluetooth de los que se está
 * recibiendo información de pulso cardíaco en tanto que elementos visuales
 * en la pantalla. Es decir, estos objetos contienen la información que se
 * muestra en la pantalla de cada dispositivo bluetooth. Esto es: dirección del
 * dispositivo, última medida de pulso, energía gastada acumulada y si está
 * o no activo. Ver layout grid_dispositivo.xml
 *
 * Además, contiene un método estático para comprobar si un pulso dado está por
 * encima del valor configurado como pulso demasiado alto.
 *
 */

public class GridDispositivo
{
    private String address;
    private int pulso;
    private int energia;
    private boolean active;

    public GridDispositivo(String address, int pulso, int energia)
    {
        this.address = address;
        this.pulso = pulso;
        this.energia = energia;

        this.active = true;
    }

    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    public int getPulso() {
        return pulso;
    }
    public void setPulso(int pulso) {
        this.pulso = pulso;
    }

    public int getEnergia() {
        return energia;
    }
    public void setEnergia(int energia) {
        this.energia = energia;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }
    public boolean isActive()
    {
        return this.active;
    }

    /**
     * Método que devuelve true si un valor de pulso está por encima
     * del valor configurado como pulso muy alto.
     * @param _pulso Valor de pulso.
     * @return True si es un valor muy alto de pulso o false en caso
     * contrario.
     */
    public static boolean alerta(int _pulso)
    {
        Context c = Intermediario.getContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        String prefPulso = c.getResources().getString(R.string.pref_max_pulso_key);
        String defaultValue = c.getResources().getString(R.string.pref_max_pulso_default_value);
        String pulsoAlto = prefs.getString(prefPulso, defaultValue);
        return _pulso >= Integer.parseInt(pulsoAlto);
    }
}
