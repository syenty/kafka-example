package com.example.msa.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.msa.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}