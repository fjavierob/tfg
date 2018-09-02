#!/bin/sh

# Script para crear un nuevo gimnasio en AWS:
# Se crea la thing GIM_XXXXX, su certificado y la política.
# Guarda los certificados y las claves publica y privada en una carpeta con nombre
# GIM_{codigo} y también obtiene la clave privada en formato pkcs8.
#
# @author fcoortbon
# 

# Configurar en esta variable el earn de nuestro AWS
mi_arn="arn:aws:iot:eu-west-1:xxxxxxxxxxxx"

# Funcion error
Error()
{
	echo $1
	echo "Saliendo..."
	cd ..
	rm -Rf $gimnasio
	exit 1
}
# Parámetro 1: número de dispositivos que van a publicar datos
n_devices=10
if [ "$1" != "" ]; then
	n_devices=$1
	echo "Número de dispositivos = $n_devices" 
else
	echo "Número de dispositivos = $n_devices (por defecto)"
fi

# Código de gimnasio: Cadena alfanúmerica aleatoria de longitud 12
longitud=12
codigo=`cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w $longitud | head -n 1`
gimnasio=GIM_$codigo
echo "Codigo gimnasio $gimnasio"

# Crear directorio para el gimnasio, donde se van a guardar sus claves y certificados
mkdir $gimnasio
if [ "$?" != 0 ]; then
	echo "Error al crear directorio $gimnasio"
	echo "Saliendo..."
	exit 1
fi
cd $gimnasio

# Crear política para el gimnasio (fichero local)
resource_connect=""
tab='    '

for i in `seq 1 $n_devices`; do
	c_id=\""$mi_arn"":client/""$codigo"_$i\"
	if [ "$i" -eq 1 ]; then
		resource_connect=${c_id}
	else
		resource_connect="$resource_connect"', \n'"${tab}""${tab}""${tab}""${tab}""$c_id"
	fi
done

policy_fichero=policy_${codigo}.json
policy_name=Policy_${codigo}

echo '{
    "Version": "2012-10-17",
    "Statement": 
    [
        {
            "Effect": "Allow",
            "Action": "iot:Publish",
            "Resource": 
            [
            	"'$mi_arn':topic/fcoortbon/centralhrp/'$gimnasio'/${iot:ClientId}",
            	"'$mi_arn':topic/fcoortbon/centralhrp/'$gimnasio'/${iot:ClientId}/status"
            ]
        },
        {
            "Effect": "Allow",
            "Action": "iot:Connect",
            "Resource":
            [
            	'"$resource_connect"'
            ]
        },
        {
        	"Effect": "Allow",
            "Action": "iot:Subscribe",
            "Resource": "'$mi_arn':topicfilter/fcoortbon/centralhrp/'$gimnasio'/${iot:ClientId}/control"
        },
        {
        	"Effect": "Allow",
            "Action": "iot:Receive",
            "Resource": "'$mi_arn':topic/fcoortbon/centralhrp/'$gimnasio'/${iot:ClientId}/control"
        }
    ]
}' > $policy_fichero

# Crear thing para el gimnasio en aws
createThingOutput=`aws iot create-thing --thing-name "$gimnasio"`
if [ "$?" != 0 ]; then
	Error "Error al crear thing en AWS"
fi
gimnasio_arn=${mi_arn}:thing/${gimnasio}

# Crear certificado y claves
createCertificateOutput=`aws iot create-keys-and-certificate --set-as-active --certificate-pem-outfile cert_${codigo}.pem --public-key-outfile publicKey_${codigo}.pem --private-key-outfile privkey_${codigo}.pem`
if [ "$?" != 0 ]; then
	aws iot delete-thing --thing-name "$gimnasio"
	Error "Error al crear certificado en AWS"
fi
certificado_arn=`echo $createCertificateOutput | cut -d'"' -f4 | head -n 1`
certificado_id=`echo $certificado_arn | cut -d'/' -f2 | head -n 1`

# Asociar el certificado a la thing del gimnasio
aws iot attach-thing-principal --principal "$certificado_arn" --thing-name "$gimnasio"
if [ "$?" != 0 ]; then
	aws iot delete-thing --thing-name "$gimnasio"
	aws iot update-certificate --new-status "INACTIVE" --certificate-id "$certificado_id"
	aws iot delete-certificate --certificate-id "$certificado_id"
	Error "Error al asociar certificado a thing"
fi

# Crear política a partir del fichero creado anteriormente
createPolicyOutput=`aws iot create-policy --policy-name "$policy_name" --policy-document file://$policy_fichero`
if [ "$?" != 0 ]; then
	aws iot detach-thing-principal --principal "$certificado_arn" --thing-name "$gimnasio"
	aws iot delete-thing --thing-name "$gimnasio"
	aws iot update-certificate --new-status "INACTIVE" --certificate-id "$certificado_id"
	aws iot delete-certificate --certificate-id "$certificado_id"
	Error "Error al crear politica"
fi

# Asociar política al certificado del gimnasio
aws iot attach-principal-policy --principal "$certificado_arn" --policy-name "$policy_name"
if [ "$?" != 0 ]; then
	aws iot detach-thing-principal --principal "$certificado_arn" --thing-name "$gimnasio"
	aws iot delete-thing --thing-name "$gimnasio"
	aws iot update-certificate --new-status "INACTIVE" --certificate-id "$certificado_id"
	aws iot delete-certificate --certificate-id "$certificado_id"
	aws iot delete-policy --policy_name "$policy_name"
	Error "Error al asociar politica $policy_name"
fi

# Asociar la política que permite manipular el estado del gimnasio 
aws iot attach-principal-policy --principal "$certificado_arn" --policy-name "Pol_shadow"
if [ "$?" != 0 ]; then
	aws iot detach-thing-principal --principal "$certificado_arn" --thing-name "$gimnasio"
	aws iot delete-thing --thing-name "$gimnasio"
	aws iot detach-principal-policy --principal "$certificado_arn" --policy-name "$policy_name"
	aws iot update-certificate --new-status "INACTIVE" --certificate-id "$certificado_id"
	aws iot delete-certificate --certificate-id "$certificado_id"
	aws iot delete-policy --policy_name "$policy_name"
	Error "Error al asociar politica Pol_shadow"
fi

# Crear clave privada en formato PKCS8
openssl pkcs8 -topk8 -inform PEM -outform PEM -in privkey_${codigo}.pem -out pkcs8_${codigo}.pem -nocrypt


echo "Creado gimnasio $gimnasio con $n_devices dispositivos"


