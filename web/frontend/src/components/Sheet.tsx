import { Drawer, Modal } from '@mantine/core';
import type { ReactNode } from 'react';
import { useIsMobile } from '../lib/useIsMobile';

/** A dialog: on a phone it slides up from the bottom (in reach of the thumb), elsewhere it is centred. */
export function Sheet({ opened, onClose, title, children }: {
  opened: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
}) {
  const mobile = useIsMobile();
  if (mobile) {
    return (
      <Drawer
        opened={opened}
        onClose={onClose}
        title={title}
        position="bottom"
        radius="lg"
        styles={{
          title: { fontWeight: 800, fontSize: 19 },
          content: { height: 'auto', maxHeight: '88dvh', flex: '0 0 auto', paddingBottom: 'env(safe-area-inset-bottom)' },
          inner: { alignItems: 'flex-end' },
        }}
      >
        {children}
      </Drawer>
    );
  }
  return (
    <Modal opened={opened} onClose={onClose} title={title} centered radius="lg" size="md"
      styles={{ title: { fontWeight: 800, fontSize: 19 } }}>
      {children}
    </Modal>
  );
}
