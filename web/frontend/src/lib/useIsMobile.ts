import { useMediaQuery } from '@mantine/hooks';

/** The phone layout: bottom navigation, sheets from below. */
export function useIsMobile(): boolean {
  return useMediaQuery('(max-width: 48em)', false, { getInitialValueInEffect: false }) ?? false;
}
