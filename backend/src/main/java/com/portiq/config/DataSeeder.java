package com.portiq.config;

import com.portiq.model.Holding;
import com.portiq.model.HoldingType;
import com.portiq.model.Portfolio;
import com.portiq.model.User;
import com.portiq.repository.HoldingRepository;
import com.portiq.repository.PortfolioRepository;
import com.portiq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seeds the single login account and, on a brand new database, a few sample holdings so the
 * app is usable immediately. Runs through JPA (not raw SQL) so encrypted columns are written
 * correctly.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.owner.username:owner}")
    private String ownerUsername;

    @Value("${app.owner.password:ChangeMe123!}")
    private String ownerPassword;

    public DataSeeder(UserRepository userRepository, PortfolioRepository portfolioRepository,
                       HoldingRepository holdingRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedOwnerAccount();
        seedSampleHoldings();
    }

    private void seedOwnerAccount() {
        if (userRepository.count() == 0) {
            User user = new User(ownerUsername, passwordEncoder.encode(ownerPassword));
            userRepository.save(user);
            log.info("Seeded login account '{}'. Change the password after first login.", ownerUsername);
        }
    }

    private void seedSampleHoldings() {
        if (portfolioRepository.count() > 0) {
            return;
        }

        Portfolio blueChip = portfolioRepository.save(
                new Portfolio("Blue Chip India", "Large-cap Indian equities focused on stable long-term growth"));
        addHolding(blueChip, "RELIANCE.NS", "Reliance Industries Ltd.", 10, "2500.00", "2023-01-15");
        addHolding(blueChip, "HDFCBANK.NS", "HDFC Bank Ltd.", 15, "1650.00", "2023-03-20");
        addHolding(blueChip, "ITC.NS", "ITC Ltd.", 50, "380.00", "2023-06-01");
        addHolding(blueChip, "TATAMOTORS.NS", "Tata Motors Ltd.", 30, "620.00", "2023-08-10");

        Portfolio itLeaders = portfolioRepository.save(
                new Portfolio("IT & Tech Leaders", "Top Indian IT and technology sector stocks"));
        addHolding(itLeaders, "TCS.NS", "Tata Consultancy Services", 5, "3500.00", "2023-02-10");
        addHolding(itLeaders, "INFY.NS", "Infosys Ltd.", 12, "1400.00", "2022-11-01");
        addHolding(itLeaders, "WIPRO.NS", "Wipro Ltd.", 20, "450.00", "2023-01-01");
        addHolding(itLeaders, "BAJFINANCE.NS", "Bajaj Finance Ltd.", 3, "7200.00", "2023-05-15");
    }

    private void addHolding(Portfolio portfolio, String ticker, String name, int quantity, String price, String date) {
        Holding holding = new Holding();
        holding.setPortfolio(portfolio);
        holding.setTicker(ticker);
        holding.setName(name);
        holding.setType(HoldingType.STOCK);
        holding.setQuantity(BigDecimal.valueOf(quantity));
        holding.setPurchasePrice(new BigDecimal(price));
        holding.setPurchaseDate(LocalDate.parse(date));
        holdingRepository.save(holding);
    }
}
