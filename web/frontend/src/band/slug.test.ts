import { describe, expect, it } from 'vitest';
import { slugOf, validSlug } from './slug';

describe('a band’s address', () => {
  it('is made from its name without accents, spaces or odd characters', () => {
    expect(slugOf('Hudba z Marsu')).toBe('hudba-z-marsu');
    expect(slugOf('  Čierne Diery!! ')).toBe('cierne-diery');
    expect(slugOf('AC/DC')).toBe('ac-dc');
  });

  it('is what the server accepts', () => {
    expect(validSlug('hudba-z-marsu')).toBe(true);
    expect(validSlug('a')).toBe(false);
    expect(validSlug('-band')).toBe(false);
    expect(validSlug('Band')).toBe(false);
    expect(validSlug(slugOf('x'.repeat(80)))).toBe(true);
  });
});
