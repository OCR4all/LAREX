package de.uniwue.zpd.dachs.larex.backend.entity.workspace;

import jakarta.persistence.*;

@Entity
@Table(name = "team_workspaces")
public class TeamWorkspace extends AbstractWorkspace {

    @Column(nullable = false, unique = true)
    private String name;

    protected TeamWorkspace() {
        super();
    }

    public TeamWorkspace(String name, String description, String ownerUserId) {
        super(ownerUserId, description);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isPersonal() {
        return false;
    }

    @Override
    public boolean canInviteUsers() {
        return true;
    }

    @Override
    public String toString() {
        return "TeamWorkspace{" +
                "id='" + getId() + '\'' +
                ", name='" + name + '\'' +
                ", ownerUserId='" + getOwnerUserId() + '\'' +
                '}';
    }
}
