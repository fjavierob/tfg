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

/**     CONTROL CLIENTE HRP
 * 
 * @author fcoortbon
 * 
 * En esta clase se recogen los distintos códigos para las órdenes y estados de
 * un dispositivo HRPClient.
 *
 */

public class ControlHRPClient
{

	public static final int COMENZAR_SESION = 1;
	public static final int TERMINAR_SESION = 2;
	public static final int PUBLICAR_ESTADO = 3;
	
	public static final int ESTADO_IDLE = 1;
	public static final int ESTADO_EN_SESION = 2;

}
