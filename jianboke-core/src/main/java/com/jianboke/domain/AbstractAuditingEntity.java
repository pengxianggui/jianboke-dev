package com.jianboke.domain;

import java.time.LocalDateTime;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.jianboke.annotation.ColumnComment;

@JsonInclude(Include.NON_NULL)
@MappedSuperclass
@Audited
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditingEntity {

	  @Id
	  @Column(name = "id", unique = true, nullable = false)
	  @GeneratedValue(strategy = GenerationType.IDENTITY)
	  private Long id;

	  @CreatedDate
	  @Column(name = "created_date", nullable = false, updatable = false)
	  @JsonIgnore
	  @ColumnComment("创建时间")
	  private LocalDateTime createdDate = LocalDateTime.now();

	  @LastModifiedDate
	  @Column(name = "last_modified_date")
	  @JsonIgnore
	  @ColumnComment("最后修改时间")
	  private LocalDateTime lastModifiedDate = LocalDateTime.now();

	public LocalDateTime getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(LocalDateTime createdDate) {
		this.createdDate = createdDate;
	}

	public LocalDateTime getLastModifiedDate() {
		return lastModifiedDate;
	}

	public void setLastModifiedDate(LocalDateTime lastModifiedDate) {
		this.lastModifiedDate = lastModifiedDate;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
