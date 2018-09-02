/**
 *  -------------------------------------------------------------------
 *  |   Fco. Javier Ortiz Bonilla.                                    |
 *  |   Escuela Técnica Superior de Ingeniería, Sevilla.              |
 *  |   Grado en Ingeniería de las Tecnologías de Telecomunicación.   |
 *  |   Trabajo fin de grado. Curso 2016/2017.                        |
 *  -------------------------------------------------------------------
 */

package fcoortbon.ble.central_hrp.controladorhrp;

import com.amazonaws.regions.Regions;

/**     CONFIGURACIÓN DE LA NUBE AWS
 *
 * @author fcoortbon
 *
 * En esta clase se recogen los parámetros necesarios para conectarse a
 * la nube de Amazon de un cliente en particular.
 * 
 * Ver https://github.com/awslabs/aws-sdk-android-samples/tree/master/AndroidPubSub
 *
 */

public class AWSConfig
{
    // IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
    protected static final String CUSTOMER_SPECIFIC_ENDPOINT = "";
    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    protected static final String COGNITO_POOL_ID = "";
    // Name of the AWS IoT policy to attach to a newly created certificate
    protected static final String AWS_IOT_POLICY_NAME = "";

    // Region of AWS IoT
    protected static final Regions MY_REGION = Regions.EU_WEST_1;
    // Filename of KeyStore file on the filesystem
    protected static final String KEYSTORE_NAME = "iot_keystore";
    // Password for the private key in the KeyStore
    protected static final String KEYSTORE_PASSWORD = "password";
    // Certificate and key aliases in the KeyStore
    protected static final String CERTIFICATE_ID = "default";
}
