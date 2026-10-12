import { Alert, Button, Center, Paper, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router';
import { useAcceptInvitation, useInvitationOffer } from '../api/hooks';
import { bandAddress } from '../band/band';
import { Loading } from '../components/Loading';
import { errorText, failed } from '../lib/notify';

/**
 * An invitation link (/invite/<token>): what it offers the signed-in user, and accepting it — only for the
 * address it was sent to; then the band's pages.
 */
export function InvitePage() {
  const { t } = useTranslation();
  const { token = '' } = useParams();
  const navigate = useNavigate();
  const offer = useInvitationOffer(token);
  const accept = useAcceptInvitation();
  if (offer.isLoading) return <Loading />;
  const o = offer.data;
  return (
    <Center mih="80dvh" p="md">
      <Paper withBorder p="xl" radius="lg" maw={480} w="100%">
        <Stack>
          <Title order={1} fz={22}>{t('invite.title')}</Title>
          {offer.isError || !o ? (
            <Alert color="red" variant="light">{offer.isError ? errorText(offer.error) : t('invite.unknown')}</Alert>
          ) : (
            <>
              <Text>{t('invite.offer', { band: o.bandName, role: t(`band.role.${o.role}`) })}</Text>
              {o.member ? <Alert variant="light">{t('invite.member')}</Alert>
                : !o.open ? <Alert color="orange" variant="light">{t('errors.invitationGone')}</Alert>
                  : !o.forYou ? <Alert color="orange" variant="light">{t('errors.invitationNotForYou', { email: o.maskedEmail })}</Alert>
                    : null}
              {o.open && o.forYou && !o.member && (
                <Button loading={accept.isPending} onClick={() => accept.mutate(token, {
                  onSuccess: (band) => navigate(bandAddress(band.slug, '/'), { replace: true }),
                  onError: failed,
                })}>
                  {t('invite.accept', { band: o.bandName })}
                </Button>
              )}
            </>
          )}
          <Button component={Link} to="/" variant="subtle">{t('invite.home')}</Button>
        </Stack>
      </Paper>
    </Center>
  );
}
