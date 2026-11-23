"""Regressões do relatório de evidências, sem PostgreSQL ou rede."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gerar_evidencias import gerar_evidencias, resumir_relatorios


def escrever_suite(diretorio, nome, *, testes=2, falhas=0, erros=0, ignorados=0):
    nome_arquivo = diretorio / ("TEST-com.bevans." + nome + ".xml")
    nome_arquivo.write_text(
        '<testsuite name="com.bevans.' + nome + '" tests="' + str(testes) +
        '" failures="' + str(falhas) + '" errors="' + str(erros) +
        '" skipped="' + str(ignorados) + '"></testsuite>',
        encoding="utf-8",
    )


class EvidenciasTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.http = self.root / "http"
        self.pg = self.root / "postgres"
        self.http.mkdir()
        self.pg.mkdir()
        for nome in ("CorsSegurancaHttpTest", "OperacaoHttpTest"):
            escrever_suite(self.http, nome)
        for nome in ("AutenticacaoPerfilPostgresIT", "OperacaoPostgresIT",
                     "TurmaCapacidadePostgresIT", "UpgradeMainSchemaPostgresIT"):
            escrever_suite(self.pg, nome)

    def test_gera_evidencia_separando_http_simulado_de_postgresql(self):
        evidencia = gerar_evidencias(self.http, self.pg, "a" * 40)
        self.assertEqual(evidencia["api_http_simulada"]["testes_executados"], 4)
        self.assertEqual(evidencia["postgresql_real_descartavel"]["testes_executados"], 8)
        self.assertFalse(evidencia["limites"]["firebase_real_verificado"])
        self.assertFalse(evidencia["limites"]["jornadas_webapp_reais_verificadas"])
        self.assertNotIn("local-test-only", json.dumps(evidencia))

    def test_rejeita_suite_essencial_ausente(self):
        (self.pg / "TEST-com.bevans.OperacaoPostgresIT.xml").unlink()
        with self.assertRaisesRegex(ValueError, "Suítes obrigatórias"):
            resumir_relatorios(self.pg, True)

    def test_rejeita_teste_com_falhas(self):
        (self.http / "TEST-com.bevans.OperacaoHttpTest.xml").unlink()
        escrever_suite(self.http, "OperacaoHttpTest", falhas=1)
        with self.assertRaisesRegex(ValueError, "falhas"):
            resumir_relatorios(self.http, False)

    def test_rejeita_teste_ignorado(self):
        (self.pg / "TEST-com.bevans.OperacaoPostgresIT.xml").unlink()
        escrever_suite(self.pg, "OperacaoPostgresIT", ignorados=1)
        with self.assertRaisesRegex(ValueError, "ignorados"):
            resumir_relatorios(self.pg, True)

    def test_rejeita_relatorio_vazio(self):
        for arquivo in self.pg.glob("TEST-*.xml"):
            arquivo.unlink()
        with self.assertRaisesRegex(ValueError, "Nenhum relatório"):
            resumir_relatorios(self.pg, True)

    def test_rejeita_sha_incompleto(self):
        with self.assertRaisesRegex(ValueError, "SHA"):
            gerar_evidencias(self.http, self.pg, "abc")

    def test_rejeita_arquivo_xml_invalido(self):
        (self.pg / "TEST-com.bevans.OperacaoPostgresIT.xml").write_text("<xml>", encoding="utf-8")
        with self.assertRaises(Exception):
            resumir_relatorios(self.pg, True)


if __name__ == "__main__":
    unittest.main()
