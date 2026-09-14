package com.extremis.hub.affiliate;

import com.extremis.hub.domain.AffiliateClick;
import com.extremis.hub.domain.AffiliateProduct;
import com.extremis.hub.repository.AffiliateClickRepository;
import com.extremis.hub.repository.AffiliateProductRepository;
import com.extremis.hub.repository.UserRepository;
import com.extremis.hub.web.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AffiliateService {

    private final AffiliateProductRepository affiliateProductRepository;
    private final AffiliateClickRepository affiliateClickRepository;
    private final UserRepository userRepository;

    public List<PublicAffiliateProductResponse> listActive() {
        return affiliateProductRepository.findAll().stream()
            .filter(AffiliateProduct::isActive)
            .map(p -> PublicAffiliateProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .brand(p.getBrand())
                .category(p.getCategory())
                .priceInfo(p.getPriceInfo())
                .merchant(p.getMerchant())
                .disclosureText(p.getDisclosureText())
                .imageUrl(p.getImageUrl())
                .build())
            .toList();
    }

    /**
     * Returns the real merchant URL to redirect to, after logging the
     * click. user is attached only if this request happened to carry
     * a valid Authorization header -- a plain <a href> navigation
     * (how this is actually reached) never sends one, so in practice
     * most clicks stay anonymous, which is expected and fine (see
     * AffiliateClick.user).
     */
    @Transactional
    public String recordClickAndGetRedirectUrl(UUID productId, String sessionRef, UUID authenticatedUserId) {
        AffiliateProduct product = affiliateProductRepository.findById(productId)
            .filter(AffiliateProduct::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("Affiliate link not found."));

        AffiliateClick click = new AffiliateClick();
        click.setAffiliateProduct(product);
        click.setSessionRef(sessionRef);
        if (authenticatedUserId != null) {
            userRepository.findById(authenticatedUserId).ifPresent(click::setUser);
        }
        affiliateClickRepository.save(click);

        return product.getAffiliateUrl();
    }
}
