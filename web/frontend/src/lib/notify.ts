import { notifications } from '@mantine/notifications';
import type { TFunction } from 'i18next';
import i18n from '../i18n';
import { ApiError } from '../api/client';
import type { QueueResult } from '../api/types';

export function done(message: string) {
  notifications.show({ message, color: 'teal', autoClose: 4500 });
}

/** What a change did, then what it queued on the platforms ("Updating Bandzone, Bandsintown…") and left out. */
export function queued(t: TFunction, what: string, result: QueueResult, name: (platform: string) => string) {
  const parts = [what];
  const byAction = new Map<string, string[]>();
  result.queued.forEach((q) => {
    const list = byAction.get(q.action) ?? [];
    if (!list.includes(name(q.platform))) list.push(name(q.platform));
    byAction.set(q.action, list);
  });
  byAction.forEach((list, action) => parts.push(t(`queue.${action}`, { list: list.join(', ') })));
  if (result.notQueued.length) parts.push(t('queue.leftOut', { list: result.notQueued.join('; ') }));
  notifications.show({ message: parts.filter(Boolean).join(' '), color: result.notQueued.length ? 'orange' : 'teal',
    autoClose: result.notQueued.length ? 9000 : 4500 });
}

/** What went wrong, in the user's language when the API named it. */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    const key = `errors.${error.code}`;
    if (i18n.exists(key)) {
      const args: Record<string, string> = { ...error.args };
      if (error.code === 'invalidGig' && args.fields) {
        args.fields = args.fields.split(',').map((f) => i18n.t(`form.field.${f}`, { defaultValue: f })).join(', ');
      }
      return i18n.t(key, args);
    }
    return error.message;
  }
  return i18n.t('errors.unexpected');
}

export function failed(error: unknown) {
  notifications.show({ message: errorText(error), color: 'red', autoClose: 8000 });
}

/** Something didn't work, as the platform or server said it (not one of the API's coded refusals). */
export function failedWith(message: string) {
  notifications.show({ message, color: 'red', autoClose: 10000 });
}
