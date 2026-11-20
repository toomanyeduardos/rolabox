// Checks that a project's deployed Firestore rules are exactly firebase/firestore.rules (ADR-010).
//
//   npm run rules:check -- --project <id>
//
// Exits 0 when they match, 1 when they differ (printing a diff), and 2 when the check can't run.
// It reads with the account from `firebase login`. Not run in CI: it needs credentials, and the
// firestore-rules job deliberately needs none.
//
// The access token comes from firebase-tools' own auth module. That module isn't a public API, but
// firebase-tools is a dev dependency pinned by package-lock.json, so it only changes when we update it.

import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const RULES_FILE = fileURLToPath(new URL('../firestore.rules', import.meta.url));
const RULES_API = 'https://firebaserules.googleapis.com/v1';
const SCOPES = ['https://www.googleapis.com/auth/cloud-platform'];

function fail(message) {
  console.error(`rules:check: ${message}`);
  process.exit(2);
}

function normalize(text) {
  return text.replace(/\r\n?/g, '\n').replace(/\n*$/, '\n');
}

async function accessToken() {
  const require = createRequire(import.meta.url);
  const auth = require('firebase-tools/lib/auth.js');
  const account = auth.getGlobalDefaultAccount();
  if (!account?.tokens?.refresh_token) {
    fail('not logged in. Run `npx firebase login` first.');
  }
  try {
    const token = await auth.getAccessToken(account.tokens.refresh_token, SCOPES);
    return token.access_token;
  } catch (error) {
    fail(`couldn't get an access token (${error.message}). Try \`npx firebase login --reauth\`.`);
  }
}

async function get(path, token) {
  const response = await fetch(`${RULES_API}/${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    return { status: response.status, message: body.error?.message ?? response.statusText };
  }
  return { status: 200, body: await response.json() };
}

// A plain line diff (longest common subsequence). The rules file is a few hundred lines.
function diff(expected, actual) {
  const a = expected.split('\n');
  const b = actual.split('\n');
  const lcs = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0));
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  const lines = [];
  let i = 0;
  let j = 0;
  while (i < a.length || j < b.length) {
    if (i < a.length && j < b.length && a[i] === b[j]) {
      i++;
      j++;
    } else if (j < b.length && (i === a.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
      lines.push(`+ ${j + 1}: ${b[j]}`);
      j++;
    } else {
      lines.push(`- ${i + 1}: ${a[i]}`);
      i++;
    }
  }
  return lines.join('\n');
}

const { values } = parseArgs({ options: { project: { type: 'string' } } });
const project = values.project;
if (!project) {
  fail('pass the project ID: npm run rules:check -- --project <id>');
}

const token = await accessToken();

const release = await get(`projects/${project}/releases/cloud.firestore`, token);
if (release.status === 404) {
  fail(`${project} has no Firestore rules deployed yet. Deploy them with npm run deploy:rules.`);
}
if (release.status !== 200) {
  fail(`couldn't read ${project}'s Firestore release (${release.status}: ${release.message}).`);
}

const ruleset = await get(release.body.rulesetName, token);
if (ruleset.status !== 200) {
  fail(`couldn't read ruleset ${release.body.rulesetName} (${ruleset.status}: ${ruleset.message}).`);
}

const files = ruleset.body.source?.files ?? [];
if (files.length !== 1) {
  fail(`the deployed ruleset has ${files.length} files; expected 1 (firestore.rules).`);
}

const repo = normalize(readFileSync(RULES_FILE, 'utf8'));
const deployed = normalize(files[0].content);
const deployedAt = ruleset.body.createTime;

if (repo === deployed) {
  console.log(`${project}: the deployed Firestore rules match firebase/firestore.rules (deployed ${deployedAt}).`);
  process.exit(0);
}

console.error(`${project}: the deployed Firestore rules (deployed ${deployedAt}) differ from firebase/firestore.rules.`);
console.error('- lines only in the repo, + lines only in the deployed rules:\n');
console.error(diff(repo, deployed));
console.error(`\nDeploy the repo's rules with: npm run deploy:rules -- --project ${project}`);
process.exit(1);
