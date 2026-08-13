-- PROTECT PROFILE FIELDS: Preventing Unauthorized Updates to Sensitive Data
-- This trigger ensures that users cannot change their own role to admin/vendor, status, commission_rate, etc.
-- Only Admins (or system service role) can modify these fields.

CREATE OR REPLACE FUNCTION public.fn_protect_profile_fields()
RETURNS TRIGGER AS $$
BEGIN
    -- If the user is NOT an admin, prevent them from changing sensitive fields
    IF public.get_user_role(auth.uid()) != 'admin' THEN

        -- Allow switching between student, professional, and nurse, but NOT to vendor or admin
        IF NEW.role IS DISTINCT FROM OLD.role THEN
            IF NEW.role NOT IN ('student', 'professional', 'nurse') OR OLD.role IN ('vendor', 'admin') THEN
                NEW.role := OLD.role;
            END IF;
        END IF;

        -- Users cannot change their own status (e.g. from pending to active)
        IF NEW.status IS DISTINCT FROM OLD.status THEN
            -- Exception: Allow active users to set themselves to something if we had an 'inactive' status
            -- But for now, we follow the schema.
            NEW.status := OLD.status;
        END IF;

        -- Users cannot change their own commission rate
        IF NEW.commission_rate IS DISTINCT FROM OLD.commission_rate THEN
            NEW.commission_rate := OLD.commission_rate;
        END IF;

        -- Users cannot change their own loyalty points
        IF NEW.loyalty_points IS DISTINCT FROM OLD.loyalty_points THEN
            NEW.loyalty_points := OLD.loyalty_points;
        END IF;

        IF NEW.loyalty_tier IS DISTINCT FROM OLD.loyalty_tier THEN
            NEW.loyalty_tier := OLD.loyalty_tier;
        END IF;

        IF NEW.is_verified_vendor IS DISTINCT FROM OLD.is_verified_vendor THEN
            NEW.is_verified_vendor := OLD.is_verified_vendor;
        END IF;

        IF NEW.id IS DISTINCT FROM OLD.id THEN
            NEW.id := OLD.id; -- Prevent changing ownership
        END IF;

        IF NEW.email IS DISTINCT FROM OLD.email THEN
            NEW.email := OLD.email; -- Prevent changing email (should be done via Auth)
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Apply the trigger to the profiles table
DROP TRIGGER IF EXISTS tr_protect_profile_fields ON public.profiles;
CREATE TRIGGER tr_protect_profile_fields
BEFORE UPDATE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.fn_protect_profile_fields();

-- Log the protection enforcement
INSERT INTO public.system_logs (action, details, severity)
VALUES ('PROFILE_SECURITY_ENFORCED', '{"description": "Added trigger to prevent non-admins from updating sensitive profile fields, while allowing student/pro role switching."}', 'info');
