package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.TurnDuration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TurnDurationRepository extends JpaRepository<TurnDuration, Long> {

    /** Zadnja trajanja poteza, najnovija prva; broj određuje pageable */
    @Query("select t from TurnDuration t where t.turn = :turn order by t.id desc")
    List<TurnDuration> findNewest(@Param("turn") String turn, Pageable pageable);

    @Query("select t.id from TurnDuration t where t.turn = :turn order by t.id desc")
    List<Long> findIdsNewestFirst(@Param("turn") String turn, Pageable pageable);

    @Modifying
    @Query("delete from TurnDuration t where t.turn = :turn and t.id <= :id")
    int deleteByTurnAndIdAtMost(@Param("turn") String turn, @Param("id") Long id);

    long countByTurn(String turn);
}
