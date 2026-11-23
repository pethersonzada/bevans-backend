#!/usr/bin/env python3
"""Gera evidência mínima e sem dados pessoais a partir de relatórios JUnit XML.

Não consulta serviços externos, não lê .env e não publica saída de testes com PII.
Só considera o banco de testes PostgreSQL descartável do próprio GitHub Actions.
"""
import argparse
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

SUITES_POSTGRES_OBRIGATORIAS = frozenset({
    "AutenticacaoPerfilPostgresIT",
    "OperacaoPostgresIT",
    "TurmaCapacidadePostgresIT",
    "UpgradeMainSchemaPostgresIT",
})
SUITES_HTTP_OBRIGATORIAS = frozenset({
    "CorsSegurancaHttpTest",
    "OperacaoHttpTest",
})


def resumir_relatorios(diretorio: Path, apenas_postgres: bool) -> dict:
    arquivos = sorted(diretorio.glob("TEST-*.xml"))
    if not arquivos:
        raise ValueError("Nenhum relatório JUnit XML encontrado.")

    suites = {}
    for arquivo in arquivos:
        raiz = ET.parse(arquivo).getroot()
        grupos = [raiz] if raiz.tag == "testsuite" else raiz.findall("testsuite")
        if not grupos:
            raise ValueError("Relatório JUnit sem testsuite.")

        for grupo in grupos:
            nome = grupo.get("name", "").rsplit(".", 1)[-1]
            if not nome:
                raise ValueError("Relatório JUnit sem nome de suíte.")
            if nome.endswith("PostgresIT") != apenas_postgres:
                continue
            if nome in suites:
                raise ValueError("Suíte repetida no relatório de evidências.")

            atributos = {}
            for chave in ("tests", "failures", "errors", "skipped"):
                valor = int(grupo.get(chave, "0"))
                if valor < 0:
                    raise ValueError("Contador JUnit inválido.")
                atributos[chave] = valor
            if atributos["tests"] < sum(atributos[k] for k in ("failures", "errors", "skipped")):
                raise ValueError("Contadores JUnit inconsistentes.")
            suites[nome] = atributos

    if not suites:
        raise ValueError("Nenhuma suíte do tipo esperado foi executada.")
    total = {k: sum(cont[k] for cont in suites.values()) for k in ("tests", "failures", "errors", "skipped")}
    if total["tests"] == 0 or total["failures"] or total["errors"] or total["skipped"]:
        raise ValueError("Testes ausentes, com falhas ou ignorados.")
    obrigatorias = SUITES_POSTGRES_OBRIGATORIAS if apenas_postgres else SUITES_HTTP_OBRIGATORIAS
    faltantes = sorted(obrigatorias - suites.keys())
    if faltantes:
        raise ValueError("Suítes obrigatórias não executadas: " + ", ".join(faltantes))
    return {
        "suítes": len(suites),
        "testes_executados": total["tests"],
        "falhas": total["failures"],
        "erros": total["errors"],
        "ignorados": total["skipped"],
        "suítes_críticas_verificadas": sorted(obrigatorias),
    }


def gerar_evidencias(api_dir: Path, postgres_dir: Path, sha: str) -> dict:
    if len(sha) != 40 or any(c not in "0123456789abcdef" for c in sha.lower()):
        raise ValueError("SHA da revisão precisa conter 40 caracteres hexadecimais.")
    return {
        "versão": 1,
        "revisão": sha.lower(),
        "status": "testes_automatizados_aprovados",
        "proveniência": "GitHub Actions",
        "ambiente": "Runner descartável com PostgreSQL 16; banco bevans_validation",
        "api_http_simulada": resumir_relatorios(api_dir, apenas_postgres=False),
        "postgresql_real_descartavel": resumir_relatorios(postgres_dir, apenas_postgres=True),
        "limites": {
            "vm_de_producao_acessada": False,
            "dados_reais_utilizados": False,
            "firebase_real_verificado": False,
            "jornadas_webapp_reais_verificadas": False,
            "gps_mapbox_real_verificado": False,
        },
        "observação": "Evidência de integração backend e regras de segurança em testes, não de jornadas completas em staging.",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-dir", required=True, type=Path)
    parser.add_argument("--postgres-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = gerar_evidencias(args.api_dir, args.postgres_dir, os.environ.get("GITHUB_SHA", ""))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("Aprovado: HTTP simulado =", result["api_http_simulada"]["testes_executados"],
              "testes; PostgreSQL descartável =", result["postgresql_real_descartavel"]["testes_executados"], "testes.")
        caminho_resumo = os.environ.get("GITHUB_STEP_SUMMARY")
        if caminho_resumo:
            with open(caminho_resumo, "a", encoding="utf-8") as resumo:
                resumo.write(
                    "## Evidências de integração BeVans\n\n"
                    f"- Revisão: `{result['revisão']}`\n"
                    f"- Suíte HTTP simulada: {result['api_http_simulada']['testes_executados']} testes\n"
                    f"- PostgreSQL 16 descartável: {result['postgresql_real_descartavel']['testes_executados']} testes\n"
                    "- Nenhum acesso à VM ou banco de produção\n"
                    "- Firebase real e jornada de navegador **não testados aqui**\n"
                )
        return 0
    except (ValueError, ET.ParseError, OSError) as exc:
        print("Falha ao gerar evidências: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
