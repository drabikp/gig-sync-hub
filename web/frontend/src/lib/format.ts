import i18n from '../i18n';

/** Dates and times as the user reads them, in the page's language. */

export function locale(): string {
  return i18n.language?.startsWith('sk') ? 'sk-SK' : 'en-GB';
}

/** How times are shown and picked: 24-hour unless the user chose 12-hour (remembered on this device). */
export type TimeFormat = '24h' | '12h';

let currentTimeFormat: TimeFormat = (() => {
  try {
    return localStorage.getItem('timeFormat') === '12h' ? '12h' : '24h';
  } catch {
    return '24h';
  }
})();

export function timeFormat(): TimeFormat {
  return currentTimeFormat;
}

export function setTimeFormat(format: TimeFormat) {
  currentTimeFormat = format;
  try {
    localStorage.setItem('timeFormat', format);
  } catch {
    // not remembered; fine
  }
}

/** "HH:mm" from "HH:mm" or "HH:mm:ss" (the value, not for showing); empty for none. */
export function hhmm(time: string | null | undefined): string {
  return time ? time.substring(0, 5) : '';
}

/** A time of day for showing: "20:30", or "8:30 PM" in the 12-hour format. */
export function timeOfDay(time: string | null | undefined): string {
  if (!time) return '';
  if (currentTimeFormat === '24h') return hhmm(time);
  const [h, m] = time.split(':').map(Number);
  return `${h % 12 || 12}:${String(m).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`;
}

/** A local day (and time) as a Date: "2026-10-17" + "20:30", or "2026-10-17T20:30[:00]". */
export function localDate(date: string, time?: string | null): Date {
  const [d, t] = date.includes('T') ? date.split('T') : [date, time ?? '00:00'];
  const [y, m, day] = d.split('-').map(Number);
  const [hh, mm] = (t || '00:00').split(':').map(Number);
  return new Date(y, m - 1, day, hh || 0, mm || 0);
}

const clean = (s: string) => s.replace(/\.$/, '');

export function dayParts(date: string) {
  const d = localDate(date);
  return {
    wd: clean(new Intl.DateTimeFormat(locale(), { weekday: 'short' }).format(d)),
    day: String(d.getDate()),
    mon: clean(new Intl.DateTimeFormat(locale(), { month: 'short' }).format(d)),
  };
}

export function longDate(date: string): string {
  return new Intl.DateTimeFormat(locale(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
    .format(localDate(date));
}

export function shortDate(date: string): string {
  return new Intl.DateTimeFormat(locale(), { day: 'numeric', month: 'short', year: 'numeric' }).format(localDate(date));
}

/** A moment (ISO instant) as "9 Oct, 14:05". */
export function when(instant: string | null | undefined): string {
  if (!instant) return '';
  return new Intl.DateTimeFormat(locale(), { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: currentTimeFormat === '12h' })
    .format(new Date(instant));
}

/** A moment's time of day, "14:05". */
export function clock(instant: string | null | undefined): string {
  if (!instant) return '';
  return new Intl.DateTimeFormat(locale(), { hour: '2-digit', minute: '2-digit', hour12: currentTimeFormat === '12h' }).format(new Date(instant));
}

/** "5 minutes ago" / "in 3 minutes". */
export function relative(instant: string | null | undefined, now = Date.now()): string {
  if (!instant) return '';
  const seconds = Math.round((new Date(instant).getTime() - now) / 1000);
  const rtf = new Intl.RelativeTimeFormat(locale(), { numeric: 'auto' });
  const abs = Math.abs(seconds);
  if (abs < 60) return rtf.format(seconds, 'second');
  if (abs < 3600) return rtf.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(seconds / 3600), 'hour');
  return rtf.format(Math.round(seconds / 86400), 'day');
}

/** The day an instant falls on, "2026-10-09" (local), for grouping. */
export function dayOf(instant: string): string {
  const d = new Date(instant);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Lower case without accents: "Košice" and "kosice" match. */
export function fold(text: string | null | undefined): string {
  return (text ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
