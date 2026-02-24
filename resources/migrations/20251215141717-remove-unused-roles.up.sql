DELETE FROM roles
WHERE role NOT IN ('owner', 'reporter', 'user-owner', 'expirer');
