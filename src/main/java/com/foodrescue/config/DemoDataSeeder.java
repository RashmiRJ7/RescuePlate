package com.foodrescue.config;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.entity.Donation;
import com.foodrescue.entity.DonationStatusHistory;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.DonationStatusHistoryRepository;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

/**
 * Seeds realistic demonstration data for development, testing, and project evaluation (Task T2).
 * <p>
 * Active under Spring profile "demo" (e.g. {@code --spring.profiles.active=demo}).
 * Seeds:
 * <ul>
 *   <li>1 Administrator</li>
 *   <li>3 Donors (Hotel Sunrise, Green Leaf Bakery, Fresh Mart Supermarket)</li>
 *   <li>3 NGOs with configured profiles (Helping Hands, Food For All, City Relief Mission)</li>
 *   <li>10 donations with varied categories, expiry times, and capacity demonstration</li>
 * </ul>
 * </p>
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final DonationRepository donationRepository;
    private final DonationStatusHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository,
                          NgoProfileRepository ngoProfileRepository,
                          DonationRepository donationRepository,
                          DonationStatusHistoryRepository historyRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.donationRepository = donationRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Running DemoDataSeeder under 'demo' profile...");

        String defaultPasswordHash = passwordEncoder.encode("Password@123");

        // 1. Seed Donors
        User donor1 = seedUserIfMissing("Hotel Sunrise", "donor1@example.com", defaultPasswordHash, Role.DONOR,
                "9845011111", "Club Road, Belagavi", 15.8500, 74.5000);
        User donor2 = seedUserIfMissing("Green Leaf Bakery", "donor2@example.com", defaultPasswordHash, Role.DONOR,
                "9845022222", "College Road, Belagavi", 15.8550, 74.5050);
        User donor3 = seedUserIfMissing("Fresh Mart Supermarket", "donor3@example.com", defaultPasswordHash, Role.DONOR,
                "9845033333", "Tilakwadi, Belagavi", 15.8600, 74.5100);

        // 2. Seed NGOs & NgoProfiles
        User ngo1 = seedUserIfMissing("Helping Hands Foundation", "ngo1@example.com", defaultPasswordHash, Role.NGO,
                "9845044444", "Camp, Belagavi", 15.8520, 74.5020);
        seedNgoProfileIfMissing(ngo1, 50, 10.0, "COOKED_MEAL,BAKERY,FRESH_PRODUCE,PACKAGED");

        User ngo2 = seedUserIfMissing("Food For All Society", "ngo2@example.com", defaultPasswordHash, Role.NGO,
                "9845055555", "Rani Chennamma Circle, Belagavi", 15.8580, 74.5080);
        seedNgoProfileIfMissing(ngo2, 30, 8.0, "COOKED_MEAL,BAKERY");

        User ngo3 = seedUserIfMissing("City Relief Mission", "ngo3@example.com", defaultPasswordHash, Role.NGO,
                "9845066666", "Bogarves, Belagavi", 15.8620, 74.5120);
        seedNgoProfileIfMissing(ngo3, 100, 15.0, "COOKED_MEAL,PACKAGED");

        // 3. Seed ~10 Donations with Varied Expiry Times and Categories
        if (donationRepository.count() == 0) {
            Instant now = Instant.now();

            seedDonation(donor1, "Veg Biryani (45 boxes)", FoodCategory.COOKED_MEAL, 45,
                    "Hot veg biryani packed in individual disposable containers",
                    "Club Road, Belagavi", 15.8500, 74.5000, now.minusSeconds(600), now.plusSeconds(35 * 60)); // 35 min left

            seedDonation(donor1, "Mixed Dal & Steamed Rice (80 meals)", FoodCategory.COOKED_MEAL, 80,
                    "Nutritious freshly cooked dal and rice, ready for immediate pickup",
                    "Club Road, Belagavi", 15.8500, 74.5000, now.minusSeconds(300), now.plusSeconds(60 * 60)); // 60 min left (Exceeds capacity for NGO1 and NGO2)

            seedDonation(donor2, "Fresh Bread Loaves & Dinner Rolls", FoodCategory.BAKERY, 35,
                    "Assorted fresh multigrain bread loaves and soft dinner rolls",
                    "College Road, Belagavi", 15.8550, 74.5050, now.minusSeconds(900), now.plusSeconds(90 * 60)); // 1.5 hr left

            seedDonation(donor1, "Paneer Butter Masala & Parathas (30 packs)", FoodCategory.COOKED_MEAL, 30,
                    "Cooked rich cottage cheese curry with freshly rolled wheat parathas",
                    "Club Road, Belagavi", 15.8500, 74.5000, now.minusSeconds(1200), now.plusSeconds(120 * 60)); // 2 hr left

            seedDonation(donor2, "Butter Croissants & Veg Puffs", FoodCategory.BAKERY, 25,
                    "Flaky croissants and savoury vegetable baked pastries",
                    "College Road, Belagavi", 15.8550, 74.5050, now.minusSeconds(1500), now.plusSeconds(180 * 60)); // 3 hr left

            seedDonation(donor3, "Fresh Apples & Organic Bananas (40 kg)", FoodCategory.FRESH_PRODUCE, 40,
                    "Fresh crisp Himachal apples and ripe Cavendish bananas",
                    "Tilakwadi, Belagavi", 15.8600, 74.5100, now.minusSeconds(1800), now.plusSeconds(240 * 60)); // 4 hr left

            seedDonation(donor3, "Farm Fresh Tomatoes & Potatoes (50 kg)", FoodCategory.FRESH_PRODUCE, 50,
                    "Harvest fresh vegetables in crates, excellent for community kitchens",
                    "Tilakwadi, Belagavi", 15.8600, 74.5100, now.minusSeconds(2000), now.plusSeconds(300 * 60)); // 5 hr left

            seedDonation(donor2, "Packaged Rusks & Tea Biscuits", FoodCategory.BAKERY, 20,
                    "Crispy wheat rusks and sweet tea biscuits in sealed packs",
                    "College Road, Belagavi", 15.8550, 74.5050, now.minusSeconds(2400), now.plusSeconds(360 * 60)); // 6 hr left

            seedDonation(donor3, "Packaged Rice, Pulses & Cooking Oil (100 units)", FoodCategory.PACKAGED, 100,
                    "Non-perishable relief food ration staples in sealed 1kg packaging",
                    "Tilakwadi, Belagavi", 15.8600, 74.5100, now.minusSeconds(3000), now.plusSeconds(600 * 60)); // 10 hr left

            seedDonation(donor1, "Executive Lunch Buffet Leftover (15 boxes)", FoodCategory.COOKED_MEAL, 15,
                    "Short shelf life lunch meal boxes kept in food warmer",
                    "Club Road, Belagavi", 15.8500, 74.5000, now.minusSeconds(100), now.plusSeconds(40 * 60)); // 40 min left

            log.info("DemoDataSeeder successfully populated 10 sample donations.");
        } else {
            log.info("Donations table already contains records; skipping demo donation seeding.");
        }
    }

    private User seedUserIfMissing(String name, String email, String passwordHash, Role role,
                                   String phone, String address, Double lat, Double lng) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User(name, email, passwordHash, role);
            user.setPhone(phone);
            user.setAddress(address);
            user.setLatitude(lat);
            user.setLongitude(lng);
            user.setActive(true);
            User saved = userRepository.save(user);
            log.info("Seeded demo user: {} ({})", name, email);
            return saved;
        });
    }

    private void seedNgoProfileIfMissing(User ngoUser, Integer capacityMeals, Double radiusKm, String acceptedCategories) {
        if (ngoProfileRepository.findByUserId(ngoUser.getId()).isEmpty()) {
            NgoProfile profile = new NgoProfile(ngoUser, capacityMeals, radiusKm, acceptedCategories);
            ngoProfileRepository.save(profile);
            log.info("Seeded demo NGO profile for: {}", ngoUser.getEmail());
        }
    }

    private void seedDonation(User donor, String foodName, FoodCategory category, int quantity,
                              String description, String address, double lat, double lng,
                              Instant readyFrom, Instant safeUntil) {
        Donation donation = new Donation(
                donor,
                foodName,
                category,
                quantity,
                address,
                lat,
                lng,
                readyFrom,
                safeUntil
        );
        donation.setDescription(description);
        donation.setStatus(DonationStatus.AVAILABLE);

        Donation saved = donationRepository.save(donation);

        DonationStatusHistory history = new DonationStatusHistory(
                saved,
                null,
                DonationStatus.AVAILABLE,
                donor,
                "Seeded demo donation posted"
        );
        historyRepository.save(history);
    }
}
