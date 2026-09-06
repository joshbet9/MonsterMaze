#!/usr/bin/env python3
import base64
import json
import os
import re
import urllib.request

repo = os.environ['GITHUB_REPOSITORY']
token = os.environ['GITHUB_TOKEN']
api = f'https://api.github.com/repos/{repo}'
headers = {
    'Accept': 'application/vnd.github+json',
    'Authorization': f'Bearer {token}',
    'X-GitHub-Api-Version': '2026-03-10',
    'User-Agent': 'MonsterMaze-release-pipeline-migration',
}

def request(path, method='GET', payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(api + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode()) if response.status != 204 else None

release = request('/contents/.github/workflows/release.yml?ref=main')
text = base64.b64decode(release['content']).decode()

oracle_preflight = '''  preflight-oracle:
    name: Preflight Oracle bot/API deployment
    runs-on: ubuntu-22.04
    environment: oracle
    timeout-minutes: 10
    steps:
      - name: Prepare SSH credentials
        shell: bash
        env:
          SSH_PRIVATE_KEY: ${{ secrets.ORACLE_SSH_PRIVATE_KEY }}
          SSH_KNOWN_HOSTS: ${{ secrets.ORACLE_SSH_KNOWN_HOSTS }}
        run: |
          set -euo pipefail
          test -n "$SSH_PRIVATE_KEY" || { echo 'ORACLE_SSH_PRIVATE_KEY is not configured.' >&2; exit 1; }
          test -n "$SSH_KNOWN_HOSTS" || { echo 'ORACLE_SSH_KNOWN_HOSTS is not configured.' >&2; exit 1; }
          mkdir -p "$HOME/.ssh"
          chmod 700 "$HOME/.ssh"
          printf '%s\\n' "$SSH_PRIVATE_KEY" > "$HOME/.ssh/monstermaze_oracle"
          printf '%s\\n' "$SSH_KNOWN_HOSTS" > "$HOME/.ssh/oracle_known_hosts"
          chmod 600 "$HOME/.ssh/monstermaze_oracle" "$HOME/.ssh/oracle_known_hosts"

      - name: Verify Oracle SSH, sudo, runtime, service, and API config
        shell: bash
        env:
          ORACLE_SSH_HOST: ${{ secrets.ORACLE_SSH_HOST }}
          ORACLE_SSH_USER: ${{ secrets.ORACLE_SSH_USER }}
          ORACLE_SSH_PORT: ${{ secrets.ORACLE_SSH_PORT }}
        run: |
          set -euo pipefail
          test -n "$ORACLE_SSH_HOST" || { echo 'ORACLE_SSH_HOST is not configured.' >&2; exit 1; }
          test -n "$ORACLE_SSH_USER" || { echo 'ORACLE_SSH_USER is not configured.' >&2; exit 1; }
          port="${ORACLE_SSH_PORT:-22}"
          awk -v host="$ORACLE_SSH_HOST" 'NF >= 3 && $2 ~ /^ssh-/ { print host " " $2 " " $3 }' "$HOME/.ssh/oracle_known_hosts" > "$HOME/.ssh/known_hosts"
          test -s "$HOME/.ssh/known_hosts" || { echo 'Oracle known_hosts contains no usable host keys.' >&2; exit 1; }
          ssh -p "$port" -i "$HOME/.ssh/monstermaze_oracle" -o BatchMode=yes -o StrictHostKeyChecking=yes \
            "${ORACLE_SSH_USER}@${ORACLE_SSH_HOST}" \
            'set -euo pipefail; sudo -n true; command -v git; command -v curl; command -v systemctl; test -f /etc/monstermaze-api.env; systemctl is-active --quiet monster-bot.service'

'''
if 'preflight-oracle:' not in text:
    text = text.replace('  release:\n', oracle_preflight + '  release:\n', 1)

text = text.replace('needs: [preflight-fly, preflight-hyperv]', 'needs: [preflight-fly, preflight-hyperv, preflight-oracle]', 1)

text, count = re.subn(r'\n      - name: Publish GitHub Release\n.*?--verify-tag\n', '\n', text, count=1, flags=re.S)
if count != 1:
    raise RuntimeError('Could not remove the old GitHub Release publication step')

assets = '''            solo/MonsterMaze-Solo.zip
            solo/solo-1.8-version.json
            MonsterMaze-Solo-1.8-plugin.jar
            solo/1.21/1.21-MonsterMaze-Solo.zip
            solo/1.21/solo-1.21-version.json
            MonsterMaze-Solo-1.21-plugin.jar
            MonsterMaze-Solo-1.21-Paper.jar
            MonsterMaze-Solo-1.21-ProtocolLib.jar
            MonsterMaze-Server-1.8.zip
            MonsterMaze-Server-1.21.zip
            DOCKER-IMAGE.txt
            SHA256SUMS.txt'''
upload = f'''\n      - name: Upload release assets for final publication
        uses: actions/upload-artifact@v4
        with:
          name: release-assets
          retention-days: 1
          path: |\n{assets}\n'''
text = text.replace('\n  deploy-fly:\n', upload + '\n  deploy-fly:\n', 1)

oracle_job = '''\n\n  deploy-oracle:
    name: Deploy release to Oracle bot/API
    needs: release
    runs-on: ubuntu-22.04
    environment: oracle
    timeout-minutes: 20
    steps:
      - name: Prepare SSH credentials
        shell: bash
        env:
          SSH_PRIVATE_KEY: ${{ secrets.ORACLE_SSH_PRIVATE_KEY }}
          SSH_KNOWN_HOSTS: ${{ secrets.ORACLE_SSH_KNOWN_HOSTS }}
        run: |
          set -euo pipefail
          test -n "$SSH_PRIVATE_KEY" || { echo 'ORACLE_SSH_PRIVATE_KEY is not configured.' >&2; exit 1; }
          test -n "$SSH_KNOWN_HOSTS" || { echo 'ORACLE_SSH_KNOWN_HOSTS is not configured.' >&2; exit 1; }
          mkdir -p "$HOME/.ssh"
          chmod 700 "$HOME/.ssh"
          printf '%s\\n' "$SSH_PRIVATE_KEY" > "$HOME/.ssh/monstermaze_oracle"
          printf '%s\\n' "$SSH_KNOWN_HOSTS" > "$HOME/.ssh/oracle_known_hosts"
          chmod 600 "$HOME/.ssh/monstermaze_oracle" "$HOME/.ssh/oracle_known_hosts"

      - name: Deploy immutable release to Oracle
        shell: bash
        env:
          ORACLE_SSH_HOST: ${{ secrets.ORACLE_SSH_HOST }}
          ORACLE_SSH_USER: ${{ secrets.ORACLE_SSH_USER }}
          ORACLE_SSH_PORT: ${{ secrets.ORACLE_SSH_PORT }}
          RELEASE_TAG: ${{ env.RELEASE_TAG }}
        run: |
          set -euo pipefail
          test -n "$ORACLE_SSH_HOST" || { echo 'ORACLE_SSH_HOST is not configured.' >&2; exit 1; }
          test -n "$ORACLE_SSH_USER" || { echo 'ORACLE_SSH_USER is not configured.' >&2; exit 1; }
          port="${ORACLE_SSH_PORT:-22}"
          awk -v host="$ORACLE_SSH_HOST" 'NF >= 3 && $2 ~ /^ssh-/ { print host " " $2 " " $3 }' "$HOME/.ssh/oracle_known_hosts" > "$HOME/.ssh/known_hosts"
          test -s "$HOME/.ssh/known_hosts" || { echo 'Oracle known_hosts contains no usable host keys.' >&2; exit 1; }
          ssh -p "$port" -i "$HOME/.ssh/monstermaze_oracle" -o BatchMode=yes -o StrictHostKeyChecking=yes \
            "${ORACLE_SSH_USER}@${ORACLE_SSH_HOST}" \
            "curl -fsSL https://raw.githubusercontent.com/joshbet9/MonsterMaze/refs/tags/${RELEASE_TAG}/ops/oracle/deploy-release.sh | bash -s -- ${RELEASE_TAG}"
'''
if '  deploy-oracle:' not in text:
    text += oracle_job

publish_job = '''\n\n  publish-release:
    name: Publish GitHub Release after all deployments
    needs: [deploy-fly, deploy-hyperv, deploy-oracle]
    runs-on: ubuntu-22.04
    permissions:
      contents: write
    timeout-minutes: 10
    steps:
      - name: Download validated release assets
        uses: actions/download-artifact@v5
        with:
          name: release-assets

      - name: Publish GitHub Release
        shell: bash
        env:
          GH_TOKEN: ${{ github.token }}
          RELEASE_TAG: ${{ env.RELEASE_TAG }}
        run: |
          set -euo pipefail
          gh release create "$RELEASE_TAG" \
            solo/MonsterMaze-Solo.zip \
            solo/solo-1.8-version.json \
            MonsterMaze-Solo-1.8-plugin.jar \
            solo/1.21/1.21-MonsterMaze-Solo.zip \
            solo/1.21/solo-1.21-version.json \
            MonsterMaze-Solo-1.21-plugin.jar \
            MonsterMaze-Solo-1.21-Paper.jar \
            MonsterMaze-Solo-1.21-ProtocolLib.jar \
            MonsterMaze-Server-1.8.zip \
            MonsterMaze-Server-1.21.zip \
            DOCKER-IMAGE.txt \
            SHA256SUMS.txt \
            --title "Monster Maze $RELEASE_TAG" \
            --generate-notes \
            --verify-tag
'''
if '  publish-release:' not in text:
    text += publish_job

payload = {
    'message': 'ci: make Oracle deployment part of release gate',
    'content': base64.b64encode(text.encode()).decode(),
    'sha': release['sha'],
    'branch': 'main',
}
request('/contents/.github/workflows/release.yml', 'PUT', payload)

try:
    oracle = request('/contents/.github/workflows/deploy-oracle.yml?ref=main')
except Exception:
    oracle = None
if oracle:
    request('/contents/.github/workflows/deploy-oracle.yml', 'DELETE', {
        'message': 'ci: remove standalone Oracle deployment workflow',
        'sha': oracle['sha'],
        'branch': 'main',
    })

print('Release pipeline migration complete.')
