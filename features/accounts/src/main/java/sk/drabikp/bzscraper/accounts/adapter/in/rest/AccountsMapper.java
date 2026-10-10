package sk.drabikp.bzscraper.accounts.adapter.in.rest;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.AccountCheckResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.MemberResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.MyBandResponse;
import sk.drabikp.bzscraper.accounts.adapter.in.rest.api.PlatformAccountResponse;
import sk.drabikp.bzscraper.accounts.application.port.in.BandsUseCase.MyBand;
import sk.drabikp.bzscraper.accounts.application.port.in.MembersUseCase.Member;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccountsUseCase.AccountView;
import sk.drabikp.bzscraper.accounts.domain.AccountCheck;
import sk.drabikp.bzscraper.gig.api.RefMapper;

import java.util.List;

/** Bands, members and platform accounts as the API sends them (accounts.yaml). */
@Mapper(uses = RefMapper.class)
interface AccountsMapper {

    @Mapping(target = "id", source = "band.id")
    @Mapping(target = "slug", source = "band.slug")
    @Mapping(target = "name", source = "band.name")
    MyBandResponse toResponse(MyBand band);

    List<MyBandResponse> toBandResponses(List<MyBand> bands);

    @Mapping(target = "name", expression = "java(member.user().shownAs())")
    @Mapping(target = "email", source = "user.email")
    MemberResponse toResponse(Member member);

    List<MemberResponse> toMemberResponses(List<Member> members);

    PlatformAccountResponse toResponse(AccountView account);

    List<PlatformAccountResponse> toAccountResponses(List<AccountView> accounts);

    AccountCheckResponse toResponse(AccountCheck check);
}
