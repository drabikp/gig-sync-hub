import { Combobox, Group, Loader, Text, TextInput, useCombobox } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconCheck, IconMapPin } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useTowns } from '../api/hooks';
import type { Country, Town } from '../api/types';

/**
 * The town of a gig: typed, with the towns of that name offered from the place search — towns
 * repeat (three Hranice, two Košice), so the one picked carries its district and coordinates
 * and the platforms can't put the gig in the wrong one. A town only typed is allowed, flagged.
 */
export function TownPicker({ city, district, country, error, onType, onPick }: {
  city: string;
  district: string | null;
  country: Country | null;
  error?: string;
  onType: (text: string) => void;
  onPick: (town: Town) => void;
}) {
  const { t } = useTranslation();
  const combobox = useCombobox({ onDropdownClose: () => combobox.resetSelectedOption() });
  const [query] = useDebouncedValue(city, 250);
  const towns = useTowns(district ? '' : query);
  const options = towns.data ?? [];
  const picked = !!district && !!city;
  return (
    <div>
      <Combobox
        store={combobox}
        onOptionSubmit={(index) => {
          onPick(options[Number(index)]);
          combobox.closeDropdown();
        }}
      >
        <Combobox.Target>
          <TextInput
            label={t('form.town')}
            placeholder={t('form.townPlaceholder')}
            value={city}
            error={error}
            autoComplete="off"
            onChange={(e) => {
              onType(e.currentTarget.value);
              combobox.openDropdown();
              combobox.updateSelectedOptionIndex();
            }}
            onFocus={() => combobox.openDropdown()}
            onBlur={() => combobox.closeDropdown()}
            rightSection={towns.isFetching ? <Loader size="xs" /> : null}
          />
        </Combobox.Target>
        <Combobox.Dropdown hidden={options.length === 0 || picked}>
          <Combobox.Options>
            {options.slice(0, 8).map((town, i) => (
              <Combobox.Option value={String(i)} key={`${town.name}-${town.district}-${i}`}>
                <Group gap="sm" wrap="nowrap">
                  <IconMapPin size={18} color="var(--mantine-color-dimmed)" />
                  <div>
                    <Text fw={700} size="sm">
                      {town.name}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {[town.district, town.country ? t(`country.${town.country}`) : null].filter(Boolean).join(' · ')}
                    </Text>
                  </div>
                </Group>
              </Combobox.Option>
            ))}
          </Combobox.Options>
        </Combobox.Dropdown>
      </Combobox>
      {picked && (
        <Group gap={6} mt={6} c="teal.8">
          <IconCheck size={15} stroke={3} />
          <Text size="sm" fw={600}>
            {[district, country ? t(`country.${country}`) : null].filter(Boolean).join(' · ')}
          </Text>
        </Group>
      )}
      {!picked && city.trim() && !combobox.dropdownOpened && (
        <Text size="sm" mt={6} c="orange.9" bg="var(--mantine-color-orange-light)" p={8} style={{ borderRadius: 10 }}>
          {t('form.notPicked')}
        </Text>
      )}
    </div>
  );
}
