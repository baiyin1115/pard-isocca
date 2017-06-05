package com.pard.modules.sys.service.impl;

import com.google.common.collect.Lists;
import com.pard.common.persistence.DataEntity;
import com.pard.common.service.impl.TreeServiceImpl;
import com.pard.common.utils.IdGen;
import com.pard.common.utils.StringUtils;
import com.pard.modules.sys.entity.Office;
import com.pard.modules.sys.repository.OfficeRepostiroy;
import com.pard.modules.sys.service.OfficeService;
import com.pard.modules.sys.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import javax.persistence.Query;
import javax.persistence.criteria.*;
import java.util.Date;
import java.util.List;

/**
 * Created by wawe on 17/5/22.
 */
@Component("officeService")
@CacheConfig(cacheNames = "offices")
public class OfficeServiceImpl extends TreeServiceImpl<Office, OfficeRepostiroy> implements OfficeService {
    @Autowired
    @Override
    protected void setRepository(OfficeRepostiroy repository) {
        this.repository = repository;
    }

    @Override
    protected String getCacheName() {
        return "offices";
    }

    @Override
    public List<Office> findByParentId(String pid) {
        if (StringUtils.isBlank(pid) || "0".equals(pid)) {
            return getRepository().findAllByParentId();
        }
        return getRepository().findAllByParentId(pid);
    }

    @Override
    public List<Office> findAllWithTree() {
        return getRepository().findAllWithTree();
    }

    @Override
    public void updateSort(List<Office> offices) {
        Query query = entityManager.createQuery("update Office m set m.sort = :sort Where m.id = :id");
        for (Office office : offices) {
            query.setParameter("sort", office.getSort());
            query.setParameter("id", office.getId());
            query.executeUpdate();
        }
        clearCache();
    }

    @Cacheable
    @Override
    public List<Office> findCompanyWithTree() {
        StringBuilder sbHql = new StringBuilder();
        sbHql.append("select new Office(id,parent.id,name)")
                .append(" ")
                .append("from Office o")
                .append(" ")
                .append("where o.delFlag = '0' and o.type = '1'")
                .append(" ")
                .append("order by o.parentIds, o.sort");
        Query query = entityManager.createQuery(sbHql.toString());
        return query.getResultList();
    }

    @Cacheable
    @Override
    public List<Office> findOfficeWithTree(String cid) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Office> cq = cb.createQuery(Office.class);
        Root<Office> root = cq.from(Office.class);
        Predicate p2 = cb.equal(root.get("delFlag").as(String.class), DataEntity.DEL_FLAG_NORMAL);
        Predicate p3 = cb.notEqual(root.get("type").as(String.class), "1");
        Predicate p4 = cb.like(root.get("parentIds").as(String.class), "%" + cid + ";%");
        Predicate p1 = cb.equal(root.get("id").as(String.class), cid);
        List<Order> orders = Lists.newArrayList();
        orders.add(cb.asc(root.get("parentIds")));
        orders.add(cb.asc(root.get("sort")));
        cq.select(cb.construct(Office.class,
                root.get("id"),
                root.get("parent").get("id"),
                root.get("name"))).where(cb.or(cb.and(p2, p3, p4), p1)).orderBy(orders);
        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    public void save(Office office) {
        if (office.getParent() != null) {
            if (office.getParent().getId().equals("0")) {
                office.setParent(null);
            } else {
                Office parent = getRepository().findOne(office.getParentId());
                if (parent != null) {
                    office.setParentIds(StringUtils.isNotBlank(parent.getParentIds()) ?
                            parent.getParentIds() + parent.getId() + ";" :
                            parent.getId() + ";"
                    );
                }
            }
        }
        super.save(office);
    }
}
