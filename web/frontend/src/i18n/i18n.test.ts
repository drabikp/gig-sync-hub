import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { en } from './en';
import { sk } from './sk';
import { problemCodeValueValues } from '../api/generated/gig';

type Tree = { [key: string]: string | Tree };

/** Every key of a bundle, plural forms reduced to their base ("count_one" → "count"). */
function keys(tree: Tree, prefix = ''): Set<string> {
  const all = new Set<string>();
  for (const [k, v] of Object.entries(tree)) {
    const key = prefix + k.replace(/_(one|few|many|other)$/, '');
    if (typeof v === 'string') all.add(key);
    else keys(v, key + '.').forEach((x) => all.add(x));
  }
  return all;
}

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((f: string) => {
    const path = join(dir, f);
    if (statSync(path).isDirectory()) return sources(path);
    return /\.(ts|tsx)$/.test(f) && !f.endsWith('.test.ts') ? [path] : [];
  });
}

describe('the language bundles', () => {
  const english = keys(en as unknown as Tree);
  const slovak = keys(sk as unknown as Tree);

  it('have the same keys in English and Slovak', () => {
    expect([...english].filter((k) => !slovak.has(k))).toEqual([]);
    expect([...slovak].filter((k) => !english.has(k))).toEqual([]);
  });

  it('say every plural in all the forms its language has', () => {
    const forms = (tree: Tree, prefix = ''): string[] =>
      Object.entries(tree).flatMap(([k, v]) => (typeof v === 'string' ? [prefix + k] : forms(v, prefix + k + '.')));
    const sk1 = forms(sk as unknown as Tree);
    for (const base of sk1.filter((k) => k.endsWith('_one')).map((k) => k.slice(0, -4))) {
      expect(sk1).toEqual(expect.arrayContaining([`${base}_few`, `${base}_many`, `${base}_other`]));
    }
  });

  it('say every code the API can say no with (the spec\'s ProblemCodeValue)', () => {
    expect(problemCodeValueValues.filter((code) => !english.has(`errors.${code}`))).toEqual([]);
  });

  it('hold every key the pages ask for by name', () => {
    const used = new Set<string>();
    for (const file of sources(join(process.cwd(), 'src'))) {
      for (const m of readFileSync(file, 'utf8').matchAll(/\bt\(\s*'([a-zA-Z0-9_.]+)'/g)) used.add(m[1]);
    }
    expect([...used].filter((k) => !english.has(k))).toEqual([]);
  });
});
