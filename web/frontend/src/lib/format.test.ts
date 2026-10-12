import i18n from '../i18n';
import { dayParts, fold, hhmm, localDate, setTimeFormat, timeOfDay } from './format';
import { applyShow } from '../pages/GigFormPage';
import type { GigDraft } from '../api/types';

describe('dates and times', () => {
  it('read a local day and time as the band reads them', () => {
    expect(localDate('2026-10-17', '20:30').getHours()).toBe(20);
    expect(localDate('2026-10-17T21:15:00').getMinutes()).toBe(15);
    expect(hhmm('20:30:00')).toBe('20:30');
  });

  it('show a time in the chosen format, the value stays 24-hour', () => {
    expect(timeOfDay('20:30:00')).toBe('20:30');
    setTimeFormat('12h');
    expect(timeOfDay('20:30:00')).toBe('8:30 PM');
    expect(timeOfDay('00:05')).toBe('12:05 AM');
    expect(timeOfDay('12:00')).toBe('12:00 PM');
    expect(hhmm('20:30:00')).toBe('20:30');
    setTimeFormat('24h');
  });

  it('say the day in the page’s language', async () => {
    await i18n.changeLanguage('sk');
    expect(dayParts('2026-10-17').day).toBe('17');
    await i18n.changeLanguage('en');
    expect(dayParts('2026-10-17').wd).toBe('Sat');
  });

  it('match towns with and without accents', () => {
    expect(fold('Košice')).toBe(fold('kosice'));
  });
});

describe('the calendar’s show on the gig form', () => {
  const gig = { date: '2026-11-14', time: '19:00', endDate: null, endTime: null, slotDate: null, slotTime: null } as unknown as GigDraft;

  it('moves the start (and the end along) when the gig is one evening', () => {
    expect(applyShow(gig, '2026-11-15', '20:30', true)).toMatchObject({ date: '2026-11-15', time: '20:30' });
    expect(applyShow({ ...gig, endDate: '2026-11-14', endTime: '23:30' }, '2026-11-16', null, true))
      .toMatchObject({ date: '2026-11-16', endDate: '2026-11-16', time: '19:00' });
  });

  it('sets the band’s slot when the gig runs over several days', () => {
    const festival = { ...gig, endDate: '2026-11-16', endTime: '23:00' };
    expect(applyShow(festival, '2026-11-15', '21:00', true)).toMatchObject({ date: '2026-11-14', slotDate: '2026-11-15', slotTime: '21:00' });
  });
});
