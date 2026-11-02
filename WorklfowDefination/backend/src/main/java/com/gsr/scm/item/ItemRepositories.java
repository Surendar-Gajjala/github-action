package com.gsr.scm.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repositories for the item aggregate. */
public final class ItemRepositories {

    private ItemRepositories() {
    }

    public interface ItemRepository extends JpaRepository<Item, Long> {

        boolean existsByItemNumberIgnoreCase(String itemNumber);

        @Query("""
                select i from Item i
                where (:q is null or lower(i.itemNumber) like :q or lower(i.name) like :q)
                  and (:status is null or i.lifecycleStatus = :status)
                """)
        Page<Item> search(@Param("q") String q, @Param("status") LifecycleStatus status, Pageable pageable);
    }

    public interface ItemPartRepository extends JpaRepository<ItemPart, Long> {

        @Query("select ip.item.id, count(ip) from ItemPart ip where ip.item.id in :itemIds group by ip.item.id")
        List<Object[]> countByItemIds(@Param("itemIds") Collection<Long> itemIds);

        @Query("select ip.part.id, count(ip) from ItemPart ip where ip.part.id in :partIds group by ip.part.id")
        List<Object[]> countByPartIds(@Param("partIds") Collection<Long> partIds);

        @Query("select ip from ItemPart ip join fetch ip.item where ip.part.id = :partId order by ip.item.itemNumber")
        List<ItemPart> findByPartIdWithItem(@Param("partId") Long partId);

        @Query("select ip from ItemPart ip join fetch ip.part where ip.item.id = :itemId order by ip.position")
        List<ItemPart> findByItemIdWithPart(@Param("itemId") Long itemId);
    }

    public interface ItemRevisionRepository extends JpaRepository<ItemRevision, Long> {

        List<ItemRevision> findByItemIdOrderByApprovedAtDesc(Long itemId);

        Optional<ItemRevision> findByItemIdAndRevision(Long itemId, String revision);
    }

    public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {

        Optional<ChangeRequest> findFirstByItemIdAndStatusOrderByIdDesc(Long itemId, ChangeRequest.Status status);

        Optional<ChangeRequest> findFirstByItemIdOrderByIdDesc(Long itemId);
    }
}
