package com.rbdip.bookstore.review;

import com.rbdip.bookstore.purchase.PurchaseHistory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Работает с отзывами, получая сведения о покупках через публичный контракт.
 */
@Service
public class ReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final PurchaseHistory purchaseHistory;

    public ReviewService(ReviewRepository reviewRepository, PurchaseHistory purchaseHistory) {
        this.reviewRepository = reviewRepository;
        this.purchaseHistory = purchaseHistory;
    }

    public Review addReview(Long productId, String authorName, Integer rating, String comment) {
        String author = authorName == null ? "anonymous" : authorName;
        boolean verifiedPurchase = purchaseHistory.hasPurchased(productId, author);
        // Как и раньше, отсутствие покупки не запрещает оставлять отзыв.
        LOG.debug("Отзыв на товар {}: подтверждённая покупка={}", productId, verifiedPurchase);
        Review review = new Review(productId, author, rating, comment);
        return reviewRepository.save(review);
    }

    public List<Review> listReviews(Long productId) {
        return reviewRepository.findByProductId(productId);
    }
}
