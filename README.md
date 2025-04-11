# DroneAPP - Atualização para SDK DJI 5.13

Este repositório contém as atualizações necessárias para compatibilidade com o SDK 5.13 da DJI, específico para o DJI Mini Pro 4.

## Alterações Principais

1. Atualizado todos os imports para usar o namespace `dji.v5` ao invés de `dji.sdk` e `dji.common`
2. Modificada a inicialização do SDK para usar as novas classes e interfaces da API V5
3. Implementada identificação correta do modelo de drone (incluindo o Mini 4 Pro)
4. Atualizados os métodos auxiliares para obter instâncias de drone, câmera, e outros componentes
5. Adicionado suporte para os recursos do KeyManager e DeviceStatusManager

## Implementação Baseada na Documentação Oficial

Esta implementação segue a documentação oficial do SDK DJI V5.13 disponível em:
https://developer.dji.com/api-reference-v5/android-api/Components/SDKManager/DJISDKManager.html

## Compatibilidade

Esta atualização torna o aplicativo compatível especificamente com o drone DJI Mini Pro 4 usando SDK 5.13.
