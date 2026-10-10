package com.privatbank.cardsender.repository;

import com.privatbank.cardsender.domain.CardType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CardTypeRepository extends JpaRepository<CardType, Long> {
    List<CardType> findAllByOrderByIdAsc();

    Optional<CardType> findByBin(String bin);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select type from CardType type where type.bin = :bin")
    Optional<CardType> findByBinForUpdate(@Param("bin") String bin);

}
