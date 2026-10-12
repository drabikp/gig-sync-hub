import { createTheme, type MantineColorsTuple } from '@mantine/core';

/** Indigo, built around #3B4FD8 (shade 6). */
const brand: MantineColorsTuple = [
  '#eef0ff',
  '#dbdefc',
  '#b4baf4',
  '#8a94ed',
  '#6773e6',
  '#505ee2',
  '#3b4fd8',
  '#3342c4',
  '#2b3aaf',
  '#1f2f9b',
];

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 6, dark: 5 },
  colors: { brand },
  fontFamily: "'Figtree Variable', system-ui, -apple-system, 'Segoe UI', sans-serif",
  headings: { fontFamily: "'Figtree Variable', system-ui, sans-serif", fontWeight: '800' },
  defaultRadius: 'md',
  cursorType: 'pointer',
  components: {
    Button: { defaultProps: { radius: 'md', size: 'md' } },
    TextInput: { defaultProps: { size: 'md', radius: 'md' } },
    Textarea: { defaultProps: { size: 'md', radius: 'md' } },
    Select: { defaultProps: { size: 'md', radius: 'md' } },
    Paper: { defaultProps: { radius: 'lg' } },
    SegmentedControl: { defaultProps: { radius: 'md' } },
  },
});
