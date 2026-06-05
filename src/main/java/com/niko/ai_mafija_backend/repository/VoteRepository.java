package com.niko.ai_mafija_backend.repository;

import com.niko.ai_mafija_backend.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote,Long> {
}
