package com.fooddelivery.inventory.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<StockItem, String> {

    List<StockItem> findAllByOrderByFoodIdAsc();

    /**
     * Takes stock in ONE atomic statement: the row is only changed if enough is left.
     * Two orders racing for the last unit cannot both win, because the database
     * checks and updates the row under a lock. Returns 1 if it worked, 0 if not enough stock.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StockItem s set s.quantity = s.quantity - :qty, s.updatedAt = :now "
            + "where s.foodId = :foodId and s.quantity >= :qty")
    int decrease(@Param("foodId") String foodId, @Param("qty") int qty, @Param("now") Instant now);

    /** Puts stock back (a released reservation). Returns the number of rows changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StockItem s set s.quantity = s.quantity + :qty, s.updatedAt = :now "
            + "where s.foodId = :foodId")
    int increase(@Param("foodId") String foodId, @Param("qty") int qty, @Param("now") Instant now);
}
