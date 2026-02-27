package de.uniwue.zpd.dachs.larex.backend.entity.workspace;

import jakarta.persistence.*;

@Entity
@Table(name = "personal_workspaces")
public class PersonalWorkspace extends AbstractWorkspace {

    private static final String PERSONAL_WORKSPACE_NAME = "Personal Workspace";

    protected PersonalWorkspace() {
        super();
    }

    public PersonalWorkspace(String ownerUserId) {
        super(ownerUserId, "Your personal workspace for private projects");
    }

    @Override
    public String getName() {
        return PERSONAL_WORKSPACE_NAME;
    }

    @Override
    public boolean isPersonal() {
        return true;
    }

    @Override
    public boolean canInviteUsers() {
        return false;
    }

    public static String generatePersonalWorkspaceId(String userId) {
        return "personal_" + userId;
    }

    @Override
    public String toString() {
        return "PersonalWorkspace{" +
                "id='" + getId() + '\'' +
                ", ownerUserId='" + getOwnerUserId() + '\'' +
                ", name='" + getName() + '\'' +
                '}';
    }
}
