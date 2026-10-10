import { Center, Loader } from '@mantine/core';

/** The whole page waiting for its first answer. */
export function Loading() {
  return (
    <Center mih="100dvh">
      <Loader />
    </Center>
  );
}
