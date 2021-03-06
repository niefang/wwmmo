package au.com.codeka.warworlds.game.alliance;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import au.com.codeka.Cash;
import au.com.codeka.common.TimeFormatter;
import au.com.codeka.common.model.BaseAllianceMember;
import au.com.codeka.common.model.BaseEmpireRank;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.TabManager;
import au.com.codeka.warworlds.eventbus.EventHandler;
import au.com.codeka.warworlds.game.empire.EnemyEmpireActivity;
import au.com.codeka.warworlds.model.Alliance;
import au.com.codeka.warworlds.model.AllianceManager;
import au.com.codeka.warworlds.model.AllianceShieldManager;
import au.com.codeka.warworlds.model.Empire;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.EmpireShieldManager;
import au.com.codeka.warworlds.model.MyEmpire;
import au.com.codeka.warworlds.model.ShieldManager;

import com.google.protobuf.InvalidProtocolBufferException;

public class AllianceDetailsFragment extends Fragment
                                     implements TabManager.Reloadable {
    private Handler mHandler;
    private Activity mActivity;
    private LayoutInflater mLayoutInflater;
    private View mView;
    private boolean mRefreshPosted;
    private int mAllianceID;
    private Alliance mAlliance;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mLayoutInflater = inflater;
        mHandler = new Handler();
        mActivity = getActivity();

        Bundle args = getArguments();
        if (args != null) {
            mAllianceID = Integer.parseInt(args.getString("au.com.codeka.warworlds.AllianceKey"));
            byte[] alliance_bytes = args.getByteArray("au.com.codeka.warworlds.Alliance");
            if (alliance_bytes != null) {
                try {
                    Messages.Alliance alliance_pb = Messages.Alliance.parseFrom(alliance_bytes);
                    mAlliance = new Alliance();
                    mAlliance.fromProtocolBuffer(alliance_pb);
                } catch (InvalidProtocolBufferException e) {
                }
            }
        }

        if (mAlliance == null && mAllianceID == 0) {
            MyEmpire myEmpire = EmpireManager.i.getEmpire();
            mAlliance = (Alliance) myEmpire.getAlliance();
            if (mAlliance != null) {
                mAllianceID = mAlliance.getID();
                mAlliance = null;
            }
        }

        Alliance myAlliance = (Alliance) EmpireManager.i.getEmpire().getAlliance();
        if (myAlliance == null) {
            mView = inflater.inflate(R.layout.alliance_details_potential, null);
        } else if (myAlliance.getID() == mAllianceID) {
            mView = inflater.inflate(R.layout.alliance_details_mine, null);
        } else {
            mView = inflater.inflate(R.layout.alliance_details_enemy, null);
        }

        AllianceManager.i.fetchAlliance(mAllianceID, new AllianceManager.FetchAllianceCompleteHandler() {
            @Override
            public void onAllianceFetched(Alliance alliance) {
                mAlliance = alliance;
                refreshAlliance();
            }
        });

        fullRefresh();
        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        AllianceManager.eventBus.register(mEventHandler);
        EmpireManager.eventBus.register(mEventHandler);
        ShieldManager.eventBus.register(mEventHandler);
    }

    @Override
    public void onStop() {
        super.onStop();
        AllianceManager.eventBus.unregister(mEventHandler);
        EmpireManager.eventBus.unregister(mEventHandler);
        ShieldManager.eventBus.unregister(mEventHandler);
    }

    private Object mEventHandler = new Object() {
        @EventHandler
        public void onAllianceUpdated(Alliance alliance) {
            if (alliance.getID() == mAllianceID) {
                mAlliance = alliance;
                fullRefresh();
            }
        }

        @EventHandler
        public void onShieldUpdated(ShieldManager.ShieldUpdatedEvent event) {
            refreshAlliance();
        }

        @EventHandler
        public void onEmpireUpdated(Empire empire) {
            MyEmpire myEmpire = EmpireManager.i.getEmpire();
            if (myEmpire.getKey().equals(empire.getKey())) {
                fullRefresh();
            } else {
                // because we send off a bunch of these at once, we can end up getting lots
                // returning at the same time. So we delay the updating for a few milliseconds
                // to ensure we don't refresh 100 times in a row...
                if (!mRefreshPosted) {
                    mRefreshPosted = true;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refreshAlliance();
                        }
                    }, 250);
                }
            }
        }
    };

    @Override
    public void reloadTab() {
        // TODO: ignore?
    }

    private void fullRefresh() {
        Button depositBtn = (Button) mView.findViewById(R.id.bank_deposit);
        if (depositBtn != null) depositBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DepositRequestDialog dialog = new DepositRequestDialog();
                dialog.setAllianceID(mAllianceID);
                dialog.show(getFragmentManager(), "");
            }
        });

        Button withdrawBtn = (Button) mView.findViewById(R.id.bank_withdraw);
        if (withdrawBtn != null) withdrawBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WithdrawRequestDialog dialog = new WithdrawRequestDialog();
                dialog.setAllianceID(mAllianceID);
                dialog.show(getFragmentManager(), "");
            }
        });

        Button changeBtn = (Button) mView.findViewById(R.id.change_details_btn);
        if (changeBtn != null) changeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(mActivity, AllianceChangeDetailsActivity.class));
            }
        });

        Button joinBtn = (Button) mView.findViewById(R.id.join_btn);
        if (joinBtn != null) joinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JoinRequestDialog dialog = new JoinRequestDialog();
                dialog.setAllianceID(mAllianceID);
                dialog.show(getFragmentManager(), "");
            }
        });

        Button leaveBtn = (Button) mView.findViewById(R.id.leave_btn);
        if (leaveBtn != null) leaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LeaveConfirmDialog dialog = new LeaveConfirmDialog();
                dialog.show(getFragmentManager(), "");
            }
        });

        if (mAlliance != null) {
            refreshAlliance();
        }
    }

    private void refreshAlliance() {
        mRefreshPosted = false;

        TextView allianceName = (TextView) mView.findViewById(R.id.alliance_name);
        allianceName.setText(mAlliance.getName());

        TextView bankBalance = (TextView) mView.findViewById(R.id.bank_balance);
        if (bankBalance != null) {
            bankBalance.setText(Cash.format((float) mAlliance.getBankBalance()));
        }

        TextView allianceMembers = (TextView) mView.findViewById(R.id.alliance_num_members);
        allianceMembers.setText(String.format("Members: %d", mAlliance.getNumMembers()));

        ImageView allianceIcon = (ImageView) mView.findViewById(R.id.alliance_icon);
        allianceIcon.setImageBitmap(AllianceShieldManager.i.getShield(mActivity, mAlliance));

        if (mAlliance.getMembers() != null) {
            ArrayList<Empire> members = new ArrayList<Empire>();
            ArrayList<Integer> missingMembers = new ArrayList<Integer>();
            for (BaseAllianceMember am : mAlliance.getMembers()) {
                Empire member = EmpireManager.i.getEmpire(am.getEmpireID());
                if (member == null) {
                    missingMembers.add(am.getEmpireID());
                } else {
                    members.add(member);
                }
            }
            LinearLayout membersList = (LinearLayout) mView.findViewById(R.id.members);
            membersList.removeAllViews();
            populateEmpireList(membersList, members);

            if (missingMembers.size() > 0) {
                EmpireManager.i.refreshEmpires(missingMembers, false);
            }
        }
    }

    private void populateEmpireList(LinearLayout parent, List<Empire> empires) {
        Collections.sort(empires, new Comparator<Empire>() {
            @Override
            public int compare(Empire lhs, Empire rhs) {
                return lhs.getDisplayName().compareTo(rhs.getDisplayName());
            }
        });

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Empire empire = (Empire) v.getTag();
                if (empire.getKey().equals(EmpireManager.i.getEmpire().getKey())) {
                    // don't show your own empire...
                    return;
                }

                Intent intent = new Intent(mActivity, EnemyEmpireActivity.class);
                intent.putExtra("au.com.codeka.warworlds.EmpireKey", empire.getKey());

                startActivity(intent);
            }
        };

        for (Empire empire : empires) {
            View view = mLayoutInflater.inflate(R.layout.alliance_empire_row, null);
            view.setTag(empire);

            ImageView empireIcon = (ImageView) view.findViewById(R.id.empire_icon);
            TextView empireName = (TextView) view.findViewById(R.id.empire_name);
            TextView lastSeen = (TextView) view.findViewById(R.id.last_seen);
            TextView totalStars = (TextView) view.findViewById(R.id.total_stars);
            TextView totalColonies = (TextView) view.findViewById(R.id.total_colonies);
            TextView totalShips = (TextView) view.findViewById(R.id.total_ships);
            TextView totalBuildings = (TextView) view.findViewById(R.id.total_buildings);
            view.findViewById(R.id.rank).setVisibility(View.INVISIBLE);

            DecimalFormat formatter = new DecimalFormat("#,##0");
            empireName.setText(empire.getDisplayName());
            empireIcon.setImageBitmap(EmpireShieldManager.i.getShield(mActivity, empire));

            if (empire.getLastSeen() == null) {
                lastSeen.setText(Html.fromHtml("Last seen: <i>never</i>"));
            } else {
                lastSeen.setText(String.format("Last seen: %s",
                        TimeFormatter.create().withMaxDays(30).format(empire.getLastSeen())));
            }

            BaseEmpireRank rank = empire.getRank();
            if (rank != null) {
                totalStars.setText(Html.fromHtml(String.format("Stars: <b>%s</b>",
                        formatter.format(rank.getTotalStars()))));
                totalColonies.setText(Html.fromHtml(String.format("Colonies: <b>%s</b>",
                        formatter.format(rank.getTotalColonies()))));

                MyEmpire myEmpire = EmpireManager.i.getEmpire();
                if (empire.getKey().equals(myEmpire.getKey()) || rank.getTotalStars() >= 10) {
                    totalShips.setText(Html.fromHtml(String.format("Ships: <b>%s</b>",
                           formatter.format(rank.getTotalShips()))));
                    totalBuildings.setText(Html.fromHtml(String.format("Buildings: <b>%s</b>",
                           formatter.format(rank.getTotalBuildings()))));
                } else {
                    totalShips.setText("");
                    totalBuildings.setText("");
                }
            } else {
                totalStars.setText("");
                totalColonies.setText("");
                totalShips.setText("");
                totalBuildings.setText("");
            }

            // they all get the same instance...
            view.setOnClickListener(onClickListener);

            parent.addView(view);
        }
    }
}
