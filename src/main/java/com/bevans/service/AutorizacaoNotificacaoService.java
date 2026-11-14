package com.bevans.service;
import com.bevans.model.*;
import com.bevans.repository.*;
import org.springframework.stereotype.Service;
@Service
public class AutorizacaoNotificacaoService {
    private final AlunoTurmaRepository vinculos;private final EmbarqueRepository embarques;private final ExcursaoRepository excursoes;
    private final RotaPublicaRepository rotas;private final InscricaoExcursaoRepository inscricoes;
    public AutorizacaoNotificacaoService(AlunoTurmaRepository v,EmbarqueRepository e,ExcursaoRepository x,RotaPublicaRepository r,InscricaoExcursaoRepository i){vinculos=v;embarques=e;excursoes=x;rotas=r;inscricoes=i;}
    public boolean direto(String contexto,Long referencia,String tipo,Long usuario,Long inscricao){
        if(inscricao!=null)return "EXCURSAO".equals(contexto)&&inscricoes.findById(inscricao).filter(i->i.getExcursao().getId().equals(referencia)).map(i->"INSCRICAO_CANCELADA".equals(tipo)||"VIAGEM_CANCELADA".equals(tipo)?i.getEstado()==StatusInscricaoExcursao.CANCELADA:i.getEstado()==StatusInscricaoExcursao.CONFIRMADA).orElse(false);
        if(usuario==null)return false;
        return switch(contexto){
            case "TURMA"->vinculos.existsByTurmaIdAndAlunoIdAndStatus(referencia,usuario,"APROVADO");
            case "ROTA_PUBLICA"->rotas.findById(referencia).map(r->vinculos.existsByTurmaIdAndAlunoIdAndStatus(r.getTurma().getId(),usuario,"APROVADO")).orElse(false);
            case "SOLICITACAO_VAGA"->vinculos.findById(referencia).map(v->"NOVA_SOLICITACAO".equals(tipo)?v.getTurma().getMotorista().getId().equals(usuario)&&"PENDENTE".equals(v.getStatus()):v.getAluno().getId().equals(usuario)&&("SOLICITACAO_APROVADA".equals(tipo)?"APROVADO":"REJEITADO").equals(v.getStatus())).orElse(false);
            case "EMBARQUE"->embarques.findById(referencia).map(v->"NOVA_SOLICITACAO".equals(tipo)?v.getRota().getTurma().getMotorista().getId().equals(usuario)&&v.getStatus()==StatusEmbarque.PENDENTE:v.getAluno().getId().equals(usuario)&&("SOLICITACAO_APROVADA".equals(tipo)?StatusEmbarque.APROVADO:StatusEmbarque.REJEITADO)==v.getStatus()).orElse(false);
            case "EXCURSAO"->excursoes.findById(referencia).map(v->v.getOrganizador().getId().equals(usuario)).orElse(false);
            default->false;
        };
    }
}
