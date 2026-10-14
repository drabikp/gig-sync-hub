import { beforeAll, describe, expect, it } from 'vitest';
import i18n from './index';

/**
 * i18next actually running, which the bundle tests never do — they read `en` and `sk` as plain
 * objects and never initialise anything, so every assertion in them would still hold with i18next
 * uninstalled. That left a whole library's runtime uncovered: the upgrade to i18next 26 and
 * react-i18next 17 passed `npm test` and `tsc` without either of them having looked up a single
 * translation.
 *
 * So: the four things the pages actually ask of it — a translation, an interpolated value, the
 * fallback to English, and Slovak's plural forms, which are the reason this app carries its own
 * plural test at all (one / few / many / other, where English has two).
 */
describe('i18next, running', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('en');
  });

  it('translates a key', () => {
    expect(i18n.t('settings.about')).toBe('About');
  });

  it('puts a value into a translation', () => {
    expect(i18n.t('gigs.count', { count: 1 })).toBe('1 gig');
    expect(i18n.t('gigs.count', { count: 7 })).toBe('7 gigs');
  });

  it('falls back to English for a language it has no bundle for', async () => {
    await i18n.changeLanguage('de');
    expect(i18n.t('settings.about')).toBe('About');
    await i18n.changeLanguage('en');
  });

  it('picks the right one of Slovak’s four plural forms', async () => {
    await i18n.changeLanguage('sk');
    expect(i18n.t('gigs.count', { count: 1 })).toBe('1 koncert');
    expect(i18n.t('gigs.count', { count: 3 })).toBe('3 koncerty');
    // "many" in Slovak, which is the form a fraction takes; the number itself is interpolated
    // as it stands, so 1.5 and not 1,5 — i18next is not formatting it for the locale here
    expect(i18n.t('gigs.count', { count: 1.5 })).toBe('1.5 koncertu');
    expect(i18n.t('gigs.count', { count: 11 })).toBe('11 koncertov');
    await i18n.changeLanguage('en');
  });
});
