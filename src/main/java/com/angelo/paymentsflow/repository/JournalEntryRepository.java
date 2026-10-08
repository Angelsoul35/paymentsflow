package com.angelo.paymentsflow.repository;

import com.angelo.paymentsflow.domain.JournalEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    /**
     * Loads an entry together with its postings and their accounts, so the entry
     * can be mapped to a response outside the posting transaction without a lazy
     * initialization error.
     */
    @Query("""
            select distinct e from JournalEntry e
            left join fetch e.postings p
            left join fetch p.account
            where e.id = :id
            """)
    Optional<JournalEntry> findByIdWithPostings(@Param("id") Long id);
}
