package com.bevans.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import java.util.*;
import org.springdoc.core.customizers.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration @ConditionalOnProperty(name="springdoc.api-docs.enabled",havingValue="true")
public class SwaggerConfig {
    @Bean public OpenAPI bevansOpenApi(){
        return new OpenAPI().info(new Info().title("BeVans Backend").version("1.0.0").contact(new io.swagger.v3.oas.models.info.Contact().name("Miguel Petherson"))
            .description("Turmas privadas, Rotas Públicas urbanas e Excursões são módulos distintos. "
                +"Identidade vem do ID token Firebase. Vínculo, titularidade e permissões são revalidados pelo servidor. "
                +"Paginação tem nomes/limites específicos por contrato. Estado físico exige confirmação humana; GPS não é prova de embarque. "
                +"Contratos detalhados e limites: docs/*-api.md do repositório. Swagger não comprova integração mobile/FCM real."))
            .components(new Components()
                .addSecuritySchemes("firebaseAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("ID token Firebase válido; nunca enviar tokens de produção em exemplos/logs"))
                .addSecuritySchemes("Convite",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Convite")
                    .description("Convite de divulgação; não concede GPS ou acesso a inscrição individual"))
                .addSecuritySchemes("Inscricao",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Inscricao")
                    .description("Credencial individual e expiráveis permissões da própria inscrição")))
            .addSecurityItem(new SecurityRequirement().addList("firebaseAuth"));
    }
    @Bean public OperationCustomizer contratosPorMetodo(){return (op,handler)->{
        if(op.getSummary()==null)op.setSummary(handler.getMethod().getName());
        if(op.getTags()==null||op.getTags().isEmpty())op.setTags(List.of(handler.getBeanType().getSimpleName().replace("Controller","")));
        String regra="Identidade da sessão; não aceitar IDs de terceiros como autorização. Erros usam ErroAPI. ";
        if(handler.getBeanType().getSimpleName().contains("Operacao"))regra+="AGUARDANDO → EMBARCADO ou NAO_EMBARCOU; EMBARCADO → DESEMBARCADO. Correção só pelo motorista, versão vigente e motivo; retry não duplica contagem. ";
        if(handler.getBeanType().getSimpleName().contains("Posicao"))regra+="GPS: TTL 30 s, intervalo mínimo 5 s, retry não renova TTL, posição expirada sem coordenadas; encerramento interrompe publicação. ";
        if(handler.getBeanType().getSimpleName().contains("Responsavel"))regra+="Sem comprovação confiável do servidor, vínculo permanece pendente e não concede operação/GPS; aceite técnico não equivale a consentimento legal. ";
        op.setDescription((op.getDescription()==null?"":op.getDescription()+"\n\n")+regra);
        return op;
    };}
    @Bean public OpenApiCustomizer contratosGerados(){return api->{
        Schema<Object> erro=new Schema<>();erro.setType("object");
        erro.addProperty("timestamp",new StringSchema().description("Data/hora local ISO, sem offset"));erro.addProperty("status",new IntegerSchema());
        erro.addProperty("erro",new StringSchema());erro.addProperty("caminho",new StringSchema());erro.setRequired(List.of("timestamp","status","erro","caminho"));
        api.getComponents().addSchemas("ErroAPI",erro);
        api.getPaths().forEach((path,item)->item.readOperationsMap().forEach((metodo,op)->{
            if(op.getParameters()!=null)for(var parametro:op.getParameters()){
                if("tamanho".equals(parametro.getName())&&parametro.getSchema()!=null){parametro.setDescription("Itens por página, de 1 a 50");parametro.getSchema().setMinimum(java.math.BigDecimal.ONE);parametro.getSchema().setMaximum(java.math.BigDecimal.valueOf(50));}
                if("pagina".equals(parametro.getName())&&parametro.getSchema()!=null){parametro.setDescription("Índice da página, base zero");parametro.getSchema().setMinimum(java.math.BigDecimal.ZERO);}
            }
            if(path.equals("/health"))op.setSecurity(List.of());
            else if(path.startsWith("/publico/excursoes/")){
                var credencial=new SecurityRequirement();
                if(path.equals("/publico/excursoes/convite")||path.equals("/publico/excursoes/credenciais")||path.equals("/publico/excursoes/inscricoes"))credencial.addList("Convite");
                if(!path.equals("/publico/excursoes/convite")&&!path.equals("/publico/excursoes/credenciais"))credencial.addList("Inscricao");
                op.setSecurity(List.of(credencial));
            }
            if(op.getResponses()!=null){
                for(var codigo:Map.of("400","Entrada inválida","401","Firebase ausente/inválido/expirado","403","Papel/permissão negados","404","Recurso ou credencial não encontrado/indisponível","409","Conflito de versão, capacidade ou estado","410","Endpoint legado desativado","429","Limite de frequência/conexões","500","Erro interno sem detalhes sensíveis","503","Serviço ou otimização indisponível").entrySet()){
                    op.getResponses().addApiResponse(codigo.getKey(),new ApiResponse().description(codigo.getValue())
                        .content(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErroAPI"))
                            .example(Map.of("timestamp","2026-11-14T12:00:00","status",Integer.parseInt(codigo.getKey()),"erro",codigo.getValue(),"caminho",path)))));
                }
                if(path.endsWith("/localizacao/eventos")){
                    String idCampo=path.startsWith("/viagens/")?"viagemId":"execucaoId";
                    op.getResponses().addApiResponse("200",new ApiResponse().description("SSE localizacao; reconectar em 10 s, máximo 5 minutos/sessão. Encerramento ou revogação fecha canal.")
                        .content(new Content().addMediaType("text/event-stream",new MediaType().schema(new StringSchema()).example("event: localizacao\nretry: 10000\ndata: {\""+idCampo+"\":1,\"estado\":\"SEM_LOCALIZACAO\",\"latitude\":null,\"longitude\":null,\"observadoEm\":null,\"recebidoEm\":null,\"precisao\":null,\"velocidade\":null,\"consultarAposSegundos\":10}\n\n"))));
                }
                if(path.endsWith("/localizacao-van"))op.setDeprecated(true);
            }
        }));
    };}
}
