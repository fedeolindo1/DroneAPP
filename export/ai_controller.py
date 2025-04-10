import os
import json
from openai import OpenAI

# the newest OpenAI model is "gpt-4o" which was released May 13, 2024.
# do not change this unless explicitly requested by the user

# Configuração do cliente OpenAI
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY")

# Definir uso do modo mock (para desenvolvimento sem API paga)
USE_MOCK = True  # Altere para False quando tiver uma API key válida com créditos

# Cliente OpenAI (apenas se tiver API key e não estiver usando o mock)
if not USE_MOCK and OPENAI_API_KEY:
    openai = OpenAI(api_key=OPENAI_API_KEY)

class DroneAIController:
    """
    Controlador de IA para o drone DJI que utiliza a API da OpenAI
    para interpretar comandos de linguagem natural e convertê-los em
    instruções para o drone.
    
    Possui um modo mock para desenvolvimento sem a necessidade de API key.
    """
    
    def __init__(self):
        self.system_prompt = """
        Você é um assistente especializado em controlar um drone DJI Mini Pro 4.
        Sua tarefa é interpretar comandos em linguagem natural e convertê-los em 
        instruções específicas para o drone.
        
        O drone pode executar as seguintes ações:
        1. Decolar (takeoff)
        2. Pousar (land)
        3. Retornar ao ponto de origem (return_home)
        4. Mover para uma coordenada específica (move_to_location)
        5. Capturar foto (take_photo)
        6. Iniciar gravação de vídeo (start_recording)
        7. Parar gravação de vídeo (stop_recording)
        8. Iniciar missão de escaneamento de códigos de barras (start_barcode_mission)
        9. Pausar missão (pause_mission)
        10. Continuar missão (resume_mission)
        11. Cancelar missão (cancel_mission)
        
        Responda sempre em formato JSON com os seguintes campos:
        {
            "action": "nome_da_ação",
            "parameters": {
                "param1": "valor1",
                "param2": "valor2"
            },
            "explanation": "Explicação do que o drone vai fazer"
        }
        """
        
    def _mock_process_command(self, user_command, drone_status=None):
        """
        Versão mock da função process_command para testes sem API OpenAI
        """
        # Palavras-chave para reconhecer comandos básicos
        command_lower = user_command.lower()
        
        if "decol" in command_lower:
            return {
                "action": "takeoff",
                "parameters": {"altitude": 5.0},
                "explanation": "Decolando para altitude de 5 metros"
            }
        elif "pous" in command_lower:
            return {
                "action": "land",
                "parameters": {},
                "explanation": "Iniciando pouso em segurança"
            }
        elif "retorn" in command_lower or "voltar" in command_lower or "casa" in command_lower:
            return {
                "action": "return_home",
                "parameters": {},
                "explanation": "Retornando ao ponto de decolagem"
            }
        elif "foto" in command_lower or "captur" in command_lower:
            return {
                "action": "take_photo",
                "parameters": {},
                "explanation": "Capturando foto"
            }
        elif "vídeo" in command_lower or "gravar" in command_lower or "video" in command_lower:
            if "parar" in command_lower or "interromp" in command_lower:
                return {
                    "action": "stop_recording",
                    "parameters": {},
                    "explanation": "Parando a gravação de vídeo"
                }
            else:
                return {
                    "action": "start_recording",
                    "parameters": {},
                    "explanation": "Iniciando gravação de vídeo"
                }
        elif "barcode" in command_lower or "código" in command_lower or "codigo" in command_lower:
            if "iniciar" in command_lower or "começar" in command_lower:
                return {
                    "action": "start_barcode_mission",
                    "parameters": {"scan_pattern": "spiral", "max_distance": 50},
                    "explanation": "Iniciando missão de escaneamento de códigos de barras em padrão espiral"
                }
            elif "pausar" in command_lower:
                return {
                    "action": "pause_mission",
                    "parameters": {},
                    "explanation": "Pausando missão atual"
                }
            elif "continuar" in command_lower or "retomar" in command_lower:
                return {
                    "action": "resume_mission",
                    "parameters": {},
                    "explanation": "Retomando missão pausada"
                }
            elif "cancelar" in command_lower:
                return {
                    "action": "cancel_mission",
                    "parameters": {},
                    "explanation": "Cancelando missão atual e retornando ao ponto inicial"
                }
        
        # Comando não reconhecido
        return {
            "action": "unknown",
            "parameters": {},
            "explanation": "Não foi possível interpretar o comando. Por favor, tente novamente."
        }
        
    def process_command(self, user_command, drone_status=None):
        """
        Processa um comando em linguagem natural e retorna a ação correspondente
        para o drone.
        
        Args:
            user_command (str): Comando em linguagem natural
            drone_status (dict, optional): Status atual do drone (bateria, altitude, etc.)
            
        Returns:
            dict: Ação a ser executada pelo drone
        """
        # Usar versão mock se configurado para tal
        if USE_MOCK:
            return self._mock_process_command(user_command, drone_status)
            
        messages = [
            {"role": "system", "content": self.system_prompt}
        ]
        
        # Adiciona o status do drone se disponível
        if drone_status:
            status_message = f"Status atual do drone:\n"
            status_message += f"Bateria: {drone_status.get('battery', 'N/A')}%\n"
            status_message += f"Altitude: {drone_status.get('altitude', 'N/A')}m\n"
            status_message += f"GPS: {drone_status.get('latitude', 'N/A')}, {drone_status.get('longitude', 'N/A')}\n"
            status_message += f"Voando: {'Sim' if drone_status.get('is_flying', False) else 'Não'}\n"
            
            messages.append({"role": "system", "content": status_message})
        
        # Adiciona o comando do usuário
        messages.append({"role": "user", "content": user_command})
        
        # Envia a solicitação para a API OpenAI
        try:
            response = openai.chat.completions.create(
                model="gpt-4o",
                messages=messages,
                response_format={"type": "json_object"}
            )
            
            # Processa a resposta
            response_text = response.choices[0].message.content
            drone_action = json.loads(response_text)
            
            return drone_action
        except Exception as e:
            return {
                "action": "error",
                "parameters": {},
                "explanation": f"Erro ao processar comando: {str(e)}"
            }

    def _mock_analyze_detected_objects(self, image_base64, drone_status=None):
        """
        Versão mock da função analyze_detected_objects para testes sem API OpenAI
        """
        # Simula detecção de objetos (resultados fixos para teste)
        return {
            "papers_detected": True,
            "barcodes_detected": True,
            "obstacles": ["caixa", "cadeira"],
            "recommended_action": "approach",
            "recommended_direction": {"x": 1.0, "y": 0.0, "z": 0.0},
            "confidence": 0.85
        }

    def analyze_detected_objects(self, image_base64, drone_status=None):
        """
        Analisa objetos detectados em uma imagem usando visão computacional da OpenAI.
        
        Args:
            image_base64 (str): Imagem codificada em base64
            drone_status (dict, optional): Status atual do drone
            
        Returns:
            dict: Análise dos objetos detectados e recomendações
        """
        # Usar versão mock se configurado para tal
        if USE_MOCK:
            return self._mock_analyze_detected_objects(image_base64, drone_status)
            
        prompt = """
        Analise esta imagem capturada pelo drone DJI Mini Pro 4 e identifique:
        1. Se há folhas de papel A4 brancas visíveis
        2. Se há códigos de barras ou QR codes visíveis
        3. Se há obstáculos que o drone deve evitar
        4. A melhor direção para o drone se mover para se aproximar dos alvos (papel A4)
        
        Responda em formato JSON com os campos:
        - papers_detected (boolean): se foram detectados papéis A4
        - barcodes_detected (boolean): se foram detectados códigos
        - obstacles (array): lista de obstáculos visíveis
        - recommended_action (string): ação recomendada para o drone
        - recommended_direction (object): direção recomendada {x, y, z} em relação à posição atual
        - confidence (float): nível de confiança da detecção entre 0 e 1
        """
        
        try:
            response = openai.chat.completions.create(
                model="gpt-4o",
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "text",
                                "text": prompt
                            },
                            {
                                "type": "image_url",
                                "image_url": {"url": f"data:image/jpeg;base64,{image_base64}"}
                            }
                        ]
                    }
                ],
                response_format={"type": "json_object"}
            )
            
            analysis = json.loads(response.choices[0].message.content)
            return analysis
        except Exception as e:
            return {
                "error": str(e),
                "papers_detected": False,
                "barcodes_detected": False,
                "obstacles": [],
                "recommended_action": "error",
                "confidence": 0.0
            }

    def _mock_generate_mission_plan(self, area_description, target_objects, constraints=None):
        """
        Versão mock da função generate_mission_plan para testes sem API OpenAI
        """
        # Plano de missão pré-definido para teste
        return {
            "mission_name": "Escaneamento de Códigos de Barras",
            "steps": [
                {
                    "step": 1,
                    "action": "takeoff",
                    "params": {"altitude": 2.0},
                    "description": "Decolar para 2 metros de altura"
                },
                {
                    "step": 2,
                    "action": "move_to",
                    "params": {"pattern": "spiral", "radius": 5.0},
                    "description": "Iniciar padrão de busca em espiral"
                },
                {
                    "step": 3,
                    "action": "scan",
                    "params": {"angle": 360, "segments": 8},
                    "description": "Escanear ambiente em 360 graus"
                },
                {
                    "step": 4,
                    "action": "approach_target",
                    "params": {"min_distance": 1.0},
                    "description": "Aproximar de alvos detectados"
                },
                {
                    "step": 5,
                    "action": "return_home",
                    "params": {},
                    "description": "Retornar ao ponto de decolagem"
                }
            ],
            "contingency_plan": {
                "low_battery": "return_home",
                "lost_signal": "hover_and_wait",
                "obstacle_detected": "avoid_and_continue"
            },
            "estimated_duration": "10 minutes",
            "estimated_battery_usage": "40%"
        }

    def generate_mission_plan(self, area_description, target_objects, constraints=None):
        """
        Gera um plano de missão com base na descrição da área e objetos alvo.
        
        Args:
            area_description (str): Descrição da área de operação
            target_objects (str): Descrição dos objetos alvo
            constraints (dict, optional): Restrições da missão (tempo, bateria, etc.)
        
        Returns:
            dict: Plano de missão gerado
        """
        # Usar versão mock se configurado para tal
        if USE_MOCK:
            return self._mock_generate_mission_plan(area_description, target_objects, constraints)
            
        prompt = f"""
        Gere um plano de missão detalhado para um drone DJI Mini Pro 4 com base nas seguintes informações:
        
        Área de operação: {area_description}
        Objetos alvo: {target_objects}
        """
        
        if constraints:
            prompt += f"\nRestrições:\n"
            for key, value in constraints.items():
                prompt += f"- {key}: {value}\n"
        
        prompt += """
        O plano de missão deve incluir:
        1. Sequência de etapas detalhadas
        2. Padrão de busca recomendado
        3. Altitudes recomendadas para diferentes fases
        4. Estratégia para maximizar a detecção de códigos de barras
        5. Plano de contingência para bateria baixa ou perda de sinal
        
        Responda em formato JSON.
        """
        
        try:
            response = openai.chat.completions.create(
                model="gpt-4o",
                messages=[
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"}
            )
            
            mission_plan = json.loads(response.choices[0].message.content)
            return mission_plan
        except Exception as e:
            return {
                "error": str(e),
                "status": "failed",
                "message": "Falha ao gerar plano de missão"
            }


# Exemplo de uso
if __name__ == "__main__":
    controller = DroneAIController()
    
    # Exemplo de processamento de comando
    command = "Decole, voe até 10 metros de altura, faça uma varredura em busca de papéis A4 e retorne para casa"
    action = controller.process_command(command)
    print(json.dumps(action, indent=2, ensure_ascii=False))
    
    # Exemplo de geração de plano de missão
    area = "Área interna de escritório com aproximadamente 100m², mesas com papéis A4 contendo códigos de barras"
    targets = "Papéis A4 brancos com códigos de barras impressos"
    constraints = {
        "tempo_máximo": "10 minutos",
        "bateria_mínima": "30%",
        "altura_máxima": "5 metros"
    }
    
    plan = controller.generate_mission_plan(area, targets, constraints)
    print(json.dumps(plan, indent=2, ensure_ascii=False))