// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use std::sync::Arc;

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
#[repr(C)]
pub struct UidRule {
    pub uid: u32,
    pub flags: u32,
    pub profile_id: u32,
}

#[derive(Clone, Debug, Default)]
pub struct PolicySnapshot {
    default_flags: u32,
    rules: Arc<[UidRule]>,
}

impl PolicySnapshot {
    pub fn new(default_flags: u32, mut rules: Vec<UidRule>) -> Result<Self, &'static str> {
        if rules.len() > MAX_UID_RULES {
            return Err("too many UID rules");
        }
        rules.sort_unstable_by_key(|rule| rule.uid);
        if rules.windows(2).any(|pair| pair[0].uid == pair[1].uid) {
            return Err("duplicate UID rule");
        }
        Ok(Self {
            default_flags,
            rules: rules.into(),
        })
    }

    #[inline]
    pub fn lookup(&self, uid: u32) -> ResolvedPolicy {
        match self.rules.binary_search_by_key(&uid, |rule| rule.uid) {
            Ok(index) => {
                let rule = self.rules[index];
                ResolvedPolicy {
                    flags: rule.flags,
                    profile_id: rule.profile_id,
                    matched: true,
                }
            }
            Err(_) => ResolvedPolicy {
                flags: self.default_flags,
                profile_id: 0,
                matched: false,
            },
        }
    }

    pub fn rules(&self) -> &[UidRule] {
        &self.rules
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ResolvedPolicy {
    pub flags: u32,
    pub profile_id: u32,
    pub matched: bool,
}

pub const MAX_UID_RULES: usize = 16_384;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn canonicalizes_once_and_looks_up_without_hashing_or_allocation() {
        let snapshot = PolicySnapshot::new(
            0x01,
            vec![
                UidRule {
                    uid: 10_005,
                    flags: 0x04,
                    profile_id: 3,
                },
                UidRule {
                    uid: 1_000,
                    flags: 0x02,
                    profile_id: 1,
                },
            ],
        )
        .unwrap();
        assert_eq!(snapshot.rules()[0].uid, 1_000);
        assert_eq!(
            snapshot.lookup(10_005),
            ResolvedPolicy {
                flags: 0x04,
                profile_id: 3,
                matched: true,
            }
        );
        assert_eq!(
            snapshot.lookup(22_222),
            ResolvedPolicy {
                flags: 0x01,
                profile_id: 0,
                matched: false,
            }
        );
    }

    #[test]
    fn rejects_duplicate_or_unbounded_rules() {
        assert!(PolicySnapshot::new(
            0,
            vec![
                UidRule {
                    uid: 42,
                    flags: 1,
                    profile_id: 0,
                },
                UidRule {
                    uid: 42,
                    flags: 2,
                    profile_id: 0,
                },
            ],
        )
        .is_err());

        let mut rules = Vec::with_capacity(MAX_UID_RULES + 1);
        for uid in 0..=MAX_UID_RULES as u32 {
            rules.push(UidRule {
                uid,
                flags: 0,
                profile_id: 0,
            });
        }
        assert!(PolicySnapshot::new(0, rules).is_err());
    }
}
