package com.course_aura_hub.organization.repository;

import com.course_aura_hub.organization.model.SeatAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, SeatAssignment.Id> {

    long countByIdSeatId(Integer seatId);

    List<SeatAssignment> findByIdSeatIdIn(Collection<Integer> seatIds);
}
