package com.bevans.dto;
import io.swagger.v3.oas.annotations.media.Schema;
/** Schemas dos corpos Map legados, sem alterar seus contratos de execução. */
public final class ContratosEntrada {
    private ContratosEntrada(){}
    public record CadastroUsuario(
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="Pessoa de exemplo")String nome,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="5581999998888")String telefone,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="pessoa@example.invalid",description="Deve corresponder ao token Firebase")String email,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="uid-da-propria-sessao")String firebaseUid,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,allowableValues={"PASSAGEIRO","MOTORISTA","RESPONSAVEL"})String tipo,
        @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,description="Somente cadastro MOTORISTA, verificado contra segredo do servidor; nunca embutir no aplicativo")String codigoAcesso){}
    public record Login(@Schema(requiredMode=Schema.RequiredMode.REQUIRED,example="pessoa@example.invalid")String email){}
    public record InicioViagem(@Schema(requiredMode=Schema.RequiredMode.REQUIRED)Long turmaId,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED,allowableValues={"IDA","VOLTA"})String sentido){}
}
