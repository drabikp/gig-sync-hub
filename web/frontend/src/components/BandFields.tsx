import { Stack, Text, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { BandForm } from '../api/types';
import { slugOf, validSlug } from '../band/slug';

/**
 * A band's name and address. The address follows the name until the user writes their own; it is
 * checked as the server checks it.
 */
export function useBandFields(initial: BandForm = { name: '', slug: '' }) {
  const [name, setName] = useState(initial.name);
  const [slug, setSlug] = useState(initial.slug);
  const [ownSlug, setOwnSlug] = useState(initial.slug !== '' && initial.slug !== slugOf(initial.name));
  const shownSlug = ownSlug ? slug : slugOf(name);
  return {
    value: { name: name.trim(), slug: shownSlug } satisfies BandForm,
    valid: name.trim().length > 0 && name.trim().length <= 100 && validSlug(shownSlug),
    setName,
    setSlug: (s: string) => {
      setOwnSlug(true);
      setSlug(s.toLowerCase());
    },
    name,
    shownSlug,
  };
}

export function BandFields({ fields }: { fields: ReturnType<typeof useBandFields> }) {
  const { t } = useTranslation();
  const slugWrong = fields.shownSlug !== '' && !validSlug(fields.shownSlug);
  return (
    <Stack gap="sm">
      <TextInput label={t('band.name')} value={fields.name} maxLength={100} required
        onChange={(e) => fields.setName(e.currentTarget.value)} />
      <TextInput label={t('band.slug')} value={fields.shownSlug} maxLength={64} required
        description={t('band.slugHelp', { address: `${window.location.host}/b/${fields.shownSlug || '…'}` })}
        error={slugWrong ? t('band.slugWrong') : undefined}
        onChange={(e) => fields.setSlug(e.currentTarget.value)} />
      <Text size="xs" c="dimmed">{t('band.publicHelp', { address: `${window.location.host}/gigs/${fields.shownSlug || '…'}` })}</Text>
    </Stack>
  );
}
