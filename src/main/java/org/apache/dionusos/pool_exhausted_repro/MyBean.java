package org.apache.dionusos.pool_exhausted_repro;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;


@Table(name = "MY_DATA")
@Entity
@NamedQueries({
        @NamedQuery(name = "GET_ALL_BEAN", query = "select a.id, a.key, a.value from MyBean a"),
})
public class MyBean {
    @Id
    private int id;

    @Basic
    @Column(name = "table_key")
    private String key = null;

    @Basic
    @Column(name = "value")
    private String value = null;

    public MyBean() {

    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "id=" + id + ", key=" + key + ", value=" + value;
    }
}
